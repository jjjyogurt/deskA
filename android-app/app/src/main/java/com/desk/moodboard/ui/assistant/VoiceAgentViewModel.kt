package com.desk.moodboard.ui.assistant

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desk.moodboard.data.model.AssistantIntentType
import com.desk.moodboard.data.model.ChatMessage
import com.desk.moodboard.data.model.EventAction
import com.desk.moodboard.data.model.EventRequest
import com.desk.moodboard.data.model.EventType
import com.desk.moodboard.data.model.NoteRequest
import com.desk.moodboard.data.model.TodoRequest
import com.desk.moodboard.data.remote.DoubaoService
import com.desk.moodboard.data.repository.CalendarRepository
import com.desk.moodboard.data.repository.NoteRepository
import com.desk.moodboard.data.repository.TodoRepository
import com.desk.moodboard.domain.ConflictDetector
import com.desk.moodboard.ui.home.CalendarViewModel
import com.desk.moodboard.voice.AudioRecorder
import com.desk.moodboard.voice.VolcengineASRService
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

open class VoiceAgentViewModel(
    private val doubaoService: DoubaoService?,
    private val todoRepository: TodoRepository,
    private val noteRepository: NoteRepository,
    private val calendarRepository: CalendarRepository,
    private val audioRecorder: AudioRecorder,
    private val volcengineASRService: VolcengineASRService,
    private val conflictDetector: ConflictDetector,
    private val calendarViewModel: CalendarViewModel
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState

    private var asrInitialized = false

    init {
        addMessage("Hi! I'm your AI assistant. How can I help?", false)
    }

    fun onSendMessage(text: String) {
        if (text.isBlank()) return
        addMessage(text, true)
        processTextInput(text)
    }

    fun onToggleRecording(context: android.content.Context) {
        viewModelScope.launch {
            if (!asrInitialized) {
                val initialized = audioRecorder.initialize()
                if (!initialized) {
                    addMessage("Microphone permission is required.", false)
                    return@launch
                }
                asrInitialized = true
            }

            if (_uiState.value.isRecording) {
                _uiState.value = _uiState.value.copy(isRecording = false, isLoading = true)
                val pcm = audioRecorder.stopRecordingRawPcm()
                val finalTranscript = withTimeoutOrNull(AsrTimeoutMs) {
                    volcengineASRService.transcribePcm(pcm)
                } ?: ""
                _uiState.value = _uiState.value.copy(isLoading = false, currentTranscript = "")

                if (finalTranscript.isNotBlank()) {
                    addMessage(finalTranscript, true)
                    processTextInput(finalTranscript)
                } else {
                    addMessage("Couldn't hear anything.", false)
                }
            } else {
                _uiState.value = _uiState.value.copy(isRecording = true, currentTranscript = "")
                audioRecorder.startRecording()
            }
        }
    }

    private fun processTextInput(text: String) {
        if (doubaoService == null) {
            addMessage("API key not configured.", false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val normalized = text.trim()
            Log.d("VoiceAgent", "Input: $normalized")

            val intent = withTimeoutOrNull(LlmTimeoutMs) {
                try {
                    Log.d("VoiceAgent", "Calling parseAssistantIntent()")
                    doubaoService.parseAssistantIntent(normalized)
                } catch (error: Exception) {
                    Log.e("VoiceAgent", "parseAssistantIntent failed", error)
                    null
                }
            }
            Log.d("VoiceAgent", "IntentResult=$intent")
            if (intent == null) {
                val earlyIntentHandled = handleEarlyIntent(normalized)
                Log.d("VoiceAgent", "EarlyIntentHandled=$earlyIntentHandled")
                if (earlyIntentHandled) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }
                addMessage("I'm having trouble right now. Try again.", false)
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            if (intent.needsClarification) {
                addMessage(intent.clarificationQuestion ?: "Could you clarify?", false)
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            when (intent.intentType) {
                AssistantIntentType.TODO -> handleTodo(intent.todo)
                AssistantIntentType.NOTE -> handleNote(intent.note)
                AssistantIntentType.EVENT -> handleEventRequest(intent.event)
                AssistantIntentType.CHAT -> addMessage(intent.chatResponse ?: "How can I help?", false)
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private fun handleEarlyIntent(text: String): Boolean {
        val normalized = text.lowercase()
        val containsDateTime = containsDateOrTimeKeyword(normalized)
        val containsEventKeyword = containsAnyKeyword(normalized, EventKeywords)
        val containsNoteKeyword = containsAnyKeyword(normalized, NoteKeywords)
        val containsTodoKeyword = containsAnyKeyword(normalized, TodoKeywords)

        if (containsNoteKeyword && !containsEventKeyword) {
            val note = NoteRequest(
                title = deriveShortTitle("", text),
                content = text,
                confidence = 0.5f
            )
            viewModelScope.launch { handleNote(note) }
            return true
        }

        if (containsTodoKeyword && !containsEventKeyword && !containsNoteKeyword && !containsDateTime) {
            val title = stripTodoPrefix(text).ifBlank { text }
            val todo = TodoRequest(
                title = title,
                description = "",
                createCalendarEvent = false,
                confidence = 0.5f
            )
            viewModelScope.launch { handleTodo(todo) }
            return true
        }

        return false
    }

    private suspend fun handleTodo(todo: TodoRequest?) {
        if (todo == null || todo.title.isBlank()) {
            addMessage("Please tell me the todo.", false)
            return
        }

        try {
            todoRepository.insertFromRequest(todo)
            addMessage("Added todo: ${todo.title}", false)
        } catch (error: Exception) {
            addMessage("Failed to add todo. Try again.", false)
            return
        }

        if (!todo.createCalendarEvent) return

        val date = todo.dueDate
        val time = todo.dueTime
        if (date == null || time == null) {
            addMessage("What time should I add to the calendar?", false)
            return
        }

        val startTime = LocalDateTime(
            date.year,
            date.month,
            date.dayOfMonth,
            time.hour,
            time.minute,
            time.second
        )

        val eventRequest = EventRequest(
            action = EventAction.CREATE,
            title = todo.title,
            description = todo.description,
            startTime = startTime,
            duration = DefaultEventDurationMinutes,
            eventType = EventType.TASK,
            confidence = todo.confidence
        )

        handleEventRequest(eventRequest)
    }

    private suspend fun handleNote(note: NoteRequest?) {
        val content = note?.content?.trim().orEmpty()
        if (content.isBlank()) {
            addMessage("Please say your idea again.", false)
            return
        }

        val shortTitle = deriveShortTitle(note?.title.orEmpty(), content)
        val sanitized = (note ?: NoteRequest()).copy(title = shortTitle, content = content)

        try {
            noteRepository.insertFromRequest(sanitized, null)
            addMessage("Saved note: $shortTitle", false)
        } catch (error: Exception) {
            addMessage("Failed to save note. Try again.", false)
        }
    }

    private suspend fun handleEventRequest(request: EventRequest?) {
        if (request == null) {
            addMessage("Could you repeat that?", false)
            return
        }
        if (request.needsClarification) {
            addMessage(request.clarificationQuestion ?: "Could you clarify?", false)
            return
        }
        if (request.action == EventAction.CREATE && request.startTime == null) {
            addMessage("What time should I schedule it?", false)
            return
        }

        when (request.action) {
            EventAction.CHAT -> addMessage(request.chatResponse ?: "How can I help you?", false)
            EventAction.CREATE -> {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val end = now.plusDays(DefaultConflictWindowDays)
                val existingEvents = calendarRepository.getEvents(now, end)
                val derivedTitle = if (request.title.isBlank()) {
                    "New event ${UUID.randomUUID().toString().take(4)}"
                } else {
                    request.title
                }
                val conflict = conflictDetector.detectConflicts(request.copy(title = derivedTitle), existingEvents)

                if (conflict.hasConflict) {
                    addMessage("Conflict: ${conflict.reasoning}", false)
                    return
                }

                val finalRequest = request.copy(title = derivedTitle)
                if (calendarRepository.createEvent(finalRequest)) {
                    addMessage("Scheduled: ${finalRequest.title}", false)
                    finalRequest.startTime?.let { calendarViewModel.selectDate(it.date) }
                    calendarViewModel.refreshEvents()
                } else {
                    addMessage("Failed to schedule.", false)
                }
            }
            EventAction.LIST -> {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val events = calendarRepository.getEvents(now, now.plusDays(1))
                if (events.isEmpty()) {
                    addMessage("No events for today.", false)
                } else {
                    val list = events.joinToString("\n") { "- ${it.title} at ${it.startTime.time}" }
                    addMessage("Today's events:\n$list", false)
                }
            }
            else -> addMessage("Not yet supported.", false)
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + ChatMessage(UUID.randomUUID().toString(), text, isUser)
        )
    }

    private fun deriveShortTitle(title: String, content: String): String {
        val titleWords = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val contentWords = content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val preferred = if (titleWords.size >= 2) titleWords else contentWords
        val shortTitle = preferred.take(3).joinToString(" ")
        return if (shortTitle.isNotBlank()) shortTitle else "Idea Note"
    }

    private fun stripTodoPrefix(text: String): String {
        val normalized = text.trim()
        val prefixes = listOf("todo:", "todo ", "to-do:", "to-do ", "task:", "task ", "add todo ", "add task ")
        val matched = prefixes.firstOrNull { normalized.lowercase().startsWith(it) }
        return matched?.let { normalized.drop(it.length).trim() } ?: normalized
    }

    private fun containsDateOrTimeKeyword(normalized: String): Boolean {
        if (DateTimeRegex.containsMatchIn(normalized)) return true
        return containsAnyKeyword(normalized, DateTimeKeywords)
    }

    private fun containsAnyKeyword(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }

    private fun LocalDateTime.plusDays(days: Int): LocalDateTime {
        val endDate = this.date.plus(days, DateTimeUnit.DAY)
        return LocalDateTime(endDate, this.time)
    }

    companion object {
        private const val AsrTimeoutMs = 10000L
        private const val LlmTimeoutMs = 8000L
        private const val DefaultConflictWindowDays = 7
        private const val DefaultEventDurationMinutes = 60

        private val DateTimeRegex = Regex("\\b\\d{1,2}:\\d{2}\\b|\\b\\d{4}-\\d{2}-\\d{2}\\b")
        private val DateTimeKeywords = listOf(
            "today", "tomorrow", "tonight", "this week", "next week",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "今天", "明天", "今晚", "本周", "下周", "周一", "周二", "周三", "周四", "周五", "周六", "周日"
        )
        private val NoteKeywords = listOf(
            "note", "idea", "idea notes", "write it down", "remember this", "jot this down",
            "capture this", "save this", "log this", "record this", "document this", "put this in notes",
            "记录一个想法", "想法", "灵感", "把这个记下来", "记下来", "写下来", "存为笔记", "保存为笔记", "idea notes"
        )
        private val TodoKeywords = listOf(
            "todo", "to-do", "task", "add todo", "add task", "remind me to"
        )
        private val EventKeywords = listOf(
            "schedule", "meeting", "calendar", "appointment", "set a reminder", "reminder",
            "安排", "会议", "日历", "预约", "提醒"
        )
    }
}

