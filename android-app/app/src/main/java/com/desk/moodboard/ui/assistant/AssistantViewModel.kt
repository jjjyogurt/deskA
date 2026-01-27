package com.desk.moodboard.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desk.moodboard.data.model.*
import com.desk.moodboard.data.remote.DoubaoService
import com.desk.moodboard.data.repository.CalendarRepository
import com.desk.moodboard.data.repository.NoteRepository
import com.desk.moodboard.data.repository.TodoRepository
import com.desk.moodboard.domain.ConflictDetector
import com.desk.moodboard.ui.home.CalendarViewModel
import com.desk.moodboard.voice.AudioRecorder
import com.desk.moodboard.voice.VolcengineASRService
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import java.util.*

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isRecording: Boolean = false,
    val isLoading: Boolean = false,
    val currentTranscript: String = "",
    val error: String? = null
)

class AssistantViewModel(
    private val doubaoService: DoubaoService?,
    private val calendarRepository: CalendarRepository,
    private val todoRepository: TodoRepository,
    private val noteRepository: NoteRepository,
    private val audioRecorder: AudioRecorder,
    private val volcengineASRService: VolcengineASRService,
    private val conflictDetector: ConflictDetector,
    private val calendarViewModel: CalendarViewModel
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState

    private var asrInitialized = false

    init {
        addMessage("Hi! I'm your Voice Agent. How can I help you today?", false)
        
        // Collect live transcript from ASR service
        viewModelScope.launch {
            volcengineASRService.transcriptFlow.collect { transcript ->
                _uiState.update { it.copy(currentTranscript = transcript) }
            }
        }
    }

    fun onSendMessage(text: String) {
        if (text.isBlank()) return
        addMessage(text, true)
        processTextInput(text)
    }

    fun onToggleRecording(context: android.content.Context) {
        viewModelScope.launch {
            if (!asrInitialized) {
                audioRecorder.initialize()
                asrInitialized = true
            }

            if (_uiState.value.isRecording) {
                _uiState.value = _uiState.value.copy(isRecording = false, isLoading = true)
                val pcm = audioRecorder.stopRecordingRawPcm()

                val audioSeconds = pcm.size / 16000.0
                val timeoutMs = ((audioSeconds * 1000.0) + 20000.0).toLong().coerceIn(20_000L, 420_000L)
                val finalTranscript = withTimeoutOrNull(timeoutMs) {
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
            
            // Unified Intent Parsing
            val intent = doubaoService.parseAssistantIntent(text)
            
            if (intent == null) {
                addMessage("I couldn't understand that. Could you rephrase?", false)
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            if (intent.needsClarification) {
                addMessage(intent.clarificationQuestion ?: "Could you clarify that?", false)
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            when (intent.intentType) {
                AssistantIntentType.TODO -> {
                    val todoReq = intent.todo
                    if (todoReq != null) {
                        todoRepository.insertFromRequest(todoReq)
                        addMessage("Done! I've added \"${todoReq.title}\" to your todos.", false)
                        if (todoReq.createCalendarEvent) {
                            handleEventRequest(intent.event ?: convertTodoToEvent(todoReq))
                        }
                    }
                }
                AssistantIntentType.EVENT -> {
                    val eventReq = intent.event
                    if (eventReq != null) {
                        handleEventRequest(eventReq)
                    }
                }
                AssistantIntentType.NOTE -> {
                    val noteReq = intent.note
                    val fallbackContent = noteReq?.content?.trim().orEmpty().ifBlank { text.trim() }
                    if (fallbackContent.isBlank()) {
                        addMessage("Please say your idea again.", false)
                        _uiState.value = _uiState.value.copy(isLoading = false)
                        return@launch
                    }
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    val safeNote = noteReq?.copy(content = fallbackContent) ?: NoteRequest(content = fallbackContent)
                    val shortTitle = deriveShortTitle(safeNote.title.orEmpty(), safeNote.content.orEmpty())
                    val sanitized = safeNote.copy(title = shortTitle)
                    try {
                        noteRepository.insertFromRequest(sanitized, null)
                        addMessage("Saved to Idea Notes: $shortTitle", false)
                    } catch (error: Exception) {
                        addMessage("I couldn't save that note. Please try again.", false)
                    }
                }
                AssistantIntentType.CHAT -> {
                    addMessage(intent.chatResponse ?: "How can I help you?", false)
                }
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private suspend fun handleEventRequest(request: EventRequest) {
        if (request.needsClarification) {
            addMessage(request.clarificationQuestion ?: "Could you clarify that?", false)
            return
        }
        when (request.action) {
            EventAction.CREATE -> {
                val derivedTitle = if (request.title.isBlank() && request.attendees.isNotEmpty()) {
                    "Meeting with ${request.attendees.joinToString(", ")}"
                } else {
                    request.title
                }
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val existingEvents = calendarRepository.getEvents(now, now.plus(7, DateTimeUnit.DAY))
                val conflict = conflictDetector.detectConflicts(request.copy(title = derivedTitle), existingEvents)
                
                if (conflict.hasConflict) {
                    addMessage("Conflict found: ${conflict.reasoning}", false)
                } else {
                    val finalRequest = request.copy(title = derivedTitle)
                    if (calendarRepository.createEvent(finalRequest)) {
                        addMessage("Scheduled: ${finalRequest.title}", false)
                        finalRequest.startTime?.let { calendarViewModel.selectDate(it.date) }
                        calendarViewModel.refreshEvents()
                    } else {
                        addMessage("Sorry, I failed to schedule that.", false)
                    }
                }
            }
            EventAction.LIST -> {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val events = calendarRepository.getEvents(now, now.plus(1, DateTimeUnit.DAY))
                if (events.isEmpty()) {
                    addMessage("You have no events for today.", false)
                } else {
                    val list = events.joinToString("\n") { "- ${it.title} at ${it.startTime.time}" }
                    addMessage("Today's events:\n$list", false)
                }
            }
            else -> addMessage("I can't do that yet.", false)
        }
    }

    private fun convertTodoToEvent(todo: TodoRequest): EventRequest {
        val date = todo.dueDate ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val time = todo.dueTime ?: LocalTime(9, 0)
        return EventRequest(
            action = EventAction.CREATE,
            title = todo.title,
            description = todo.description,
            startTime = LocalDateTime(date, time),
            duration = 60,
            eventType = EventType.TASK,
            confidence = todo.confidence
        )
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
        val padded = if (preferred.size >= 2) {
            preferred
        } else {
            preferred + List(2 - preferred.size) { "Idea" }
        }
        return padded.take(3).joinToString(" ").ifBlank { "Idea Note" }
    }

    private fun LocalDateTime.plus(value: Int, unit: DateTimeUnit): LocalDateTime {
        return this.toInstant(TimeZone.currentSystemDefault()).plus(value, unit, TimeZone.currentSystemDefault()).toLocalDateTime(TimeZone.currentSystemDefault())
    }
}
