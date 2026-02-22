package com.desk.moodboard.ui.home

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desk.moodboard.R
import com.desk.moodboard.data.model.AssistantIntentType
import com.desk.moodboard.data.model.EventAction
import com.desk.moodboard.data.model.EventRequest
import com.desk.moodboard.data.model.EventType
import com.desk.moodboard.data.model.NoteItem
import com.desk.moodboard.data.model.NoteRequest
import com.desk.moodboard.data.model.TodoItem
import com.desk.moodboard.data.model.TodoRequest
import com.desk.moodboard.data.remote.DoubaoService
import com.desk.moodboard.data.repository.CalendarCreateResult
import com.desk.moodboard.data.repository.CalendarRepository
import com.desk.moodboard.data.repository.NoteRepository
import com.desk.moodboard.data.repository.TodoRepository
import com.desk.moodboard.domain.ConflictDetector
import com.desk.moodboard.voice.AudioRecorder
import com.desk.moodboard.voice.VolcengineASRService
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

enum class TodoFilter {
    TODAY,
    ALL,
    COMPLETED
}

data class TodoUiState(
    val todos: List<TodoItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val isRecording: Boolean = false,
    val isLoading: Boolean = false,
    val isNoteRecording: Boolean = false,
    val isNoteLoading: Boolean = false,
    val isNoteSessionActive: Boolean = false,
    val activeNoteSessionId: String? = null,
    val statusMessage: String? = null,
    val noteStatusMessage: String? = null,
    val selectedFilter: TodoFilter = TodoFilter.TODAY
)

class TodoViewModel(
    private val appContext: Context,
    private val doubaoService: DoubaoService?,
    private val todoRepository: TodoRepository,
    private val noteRepository: NoteRepository,
    private val calendarRepository: CalendarRepository,
    private val audioRecorder: AudioRecorder,
    private val volcengineASRService: VolcengineASRService,
    private val conflictDetector: ConflictDetector,
    private val calendarViewModel: CalendarViewModel
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodoUiState())
    val uiState: StateFlow<TodoUiState> = _uiState

    private var asrInitialized = false
    private var recordingJob: Job? = null
    private var noteSessionId: String? = null

    init {
        viewModelScope.launch {
            todoRepository.observeTodos().collect { items ->
                _uiState.update { it.copy(todos = items) }
            }
        }
        viewModelScope.launch {
            noteRepository.observeNotes().collect { items ->
                _uiState.update { it.copy(notes = items) }
            }
        }
    }

    fun onSelectFilter(filter: TodoFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun onToggleTodoDone(todo: TodoItem) {
        viewModelScope.launch {
            todoRepository.update(todo.copy(isDone = !todo.isDone))
        }
    }

    fun onToggleRecording(context: android.content.Context) {
        viewModelScope.launch {
            if (!asrInitialized) {
                audioRecorder.initialize()
                asrInitialized = true
            }

            if (_uiState.value.isRecording) {
                _uiState.update { it.copy(isRecording = false, isLoading = true) }
                val pcm = audioRecorder.stopRecordingRawPcm()
                val finalTranscript = withTimeoutOrNull(10000) {
                    volcengineASRService.transcribePcm(pcm)
                } ?: ""
                _uiState.update { it.copy(isLoading = false) }

                recordingJob?.cancel()
                volcengineASRService.stopStreaming()

                if (finalTranscript.isNotBlank()) {
                    processTextInput(finalTranscript)
                } else {
                    _uiState.update { it.copy(statusMessage = s(R.string.assistant_error_could_not_hear)) }
                }
            } else {
                _uiState.update { it.copy(isRecording = true, statusMessage = s(R.string.home_voice_agent_status_listening)) }
                audioRecorder.startRecording()
            }
        }
    }

    fun startNoteSession() {
        if (_uiState.value.isNoteSessionActive) {
            return
        }
        val newSessionId = UUID.randomUUID().toString()
        noteSessionId = newSessionId
        _uiState.update {
            it.copy(
                isNoteSessionActive = true,
                activeNoteSessionId = newSessionId,
                noteStatusMessage = s(R.string.todo_note_session_started)
            )
        }
    }

    fun stopNoteSession() {
        if (_uiState.value.isNoteRecording) {
            audioRecorder.stopRecordingRawPcm()
            volcengineASRService.stopStreaming()
        }
        noteSessionId = null
        _uiState.update {
            it.copy(
                isNoteSessionActive = false,
                activeNoteSessionId = null,
                isNoteRecording = false,
                isNoteLoading = false,
                noteStatusMessage = s(R.string.todo_note_session_stopped)
            )
        }
    }

    fun onToggleNoteRecording(context: android.content.Context) {
        viewModelScope.launch {
            if (!_uiState.value.isNoteSessionActive) {
                _uiState.update { it.copy(noteStatusMessage = s(R.string.todo_note_start_session_first)) }
                return@launch
            }
            if (_uiState.value.isRecording) {
                _uiState.update { it.copy(noteStatusMessage = s(R.string.todo_note_finish_todo_recording_first)) }
                return@launch
            }
            if (!asrInitialized) {
                audioRecorder.initialize()
                asrInitialized = true
            }

            if (_uiState.value.isNoteRecording) {
                _uiState.update { it.copy(isNoteRecording = false, isNoteLoading = true) }
                val pcm = audioRecorder.stopRecordingRawPcm()
                val finalTranscript = withTimeoutOrNull(10000) {
                    volcengineASRService.transcribePcm(pcm)
                } ?: ""
                _uiState.update { it.copy(isNoteLoading = false) }

                recordingJob?.cancel()
                volcengineASRService.stopStreaming()

                if (finalTranscript.isNotBlank()) {
                    processNoteTextInput(finalTranscript)
                } else {
                    _uiState.update { it.copy(noteStatusMessage = s(R.string.assistant_error_could_not_hear)) }
                }
            } else {
                _uiState.update { it.copy(isNoteRecording = true, noteStatusMessage = s(R.string.todo_note_listening_for_ideas)) }
                audioRecorder.startRecording()
            }
        }
    }

    private fun processTextInput(text: String) {
        if (doubaoService == null) {
            _uiState.update { it.copy(statusMessage = s(R.string.assistant_error_api_key_missing)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = s(R.string.todo_processing)) }
            val intent = doubaoService.parseAssistantIntent(text)
            if (intent == null) {
                _uiState.update { it.copy(isLoading = false, statusMessage = s(R.string.todo_could_not_understand)) }
                return@launch
            }

            if (intent.needsClarification) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = intent.clarificationQuestion ?: s(R.string.todo_could_you_clarify)
                    )
                }
                return@launch
            }

            when (intent.intentType) {
                AssistantIntentType.TODO -> handleTodo(intent.todo)
                AssistantIntentType.EVENT -> handleEvent(intent.event)
                AssistantIntentType.NOTE -> handleNote(intent.note)
                AssistantIntentType.CHAT -> {
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = intent.chatResponse ?: s(R.string.todo_how_can_i_help))
                    }
                }
            }
        }
    }

    private suspend fun handleTodo(todo: TodoRequest?) {
        if (todo == null || todo.title.isBlank()) {
            _uiState.update { it.copy(isLoading = false, statusMessage = s(R.string.assistant_prompt_tell_todo)) }
            return
        }

        todoRepository.insertFromRequest(todo)
        _uiState.update { it.copy(statusMessage = s(R.string.assistant_todo_added, todo.title)) }

        if (todo.createCalendarEvent) {
            val date = todo.dueDate
            val time = todo.dueTime
            if (date == null || time == null) {
                _uiState.update { it.copy(isLoading = false, statusMessage = s(R.string.assistant_prompt_todo_calendar_time)) }
                return
            }
            val startTime = LocalDateTime(date.year, date.month, date.dayOfMonth, time.hour, time.minute, time.second)
            val eventRequest = EventRequest(
                action = EventAction.CREATE,
                title = todo.title,
                description = todo.description,
                startTime = startTime,
                duration = 60,
                eventType = EventType.TASK,
                confidence = todo.confidence
            )
            handleEvent(eventRequest)
        } else {
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun processNoteTextInput(text: String) {
        if (doubaoService == null) {
            _uiState.update { it.copy(noteStatusMessage = s(R.string.assistant_error_api_key_missing)) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isNoteLoading = true, noteStatusMessage = s(R.string.todo_processing_note)) }
            val intent = try {
                doubaoService.parseAssistantIntent(text)
            } catch (error: Exception) {
                _uiState.update { it.copy(isNoteLoading = false, noteStatusMessage = s(R.string.todo_note_processing_failed)) }
                return@launch
            }
            if (intent == null) {
                _uiState.update { it.copy(isNoteLoading = false, noteStatusMessage = s(R.string.todo_could_not_understand)) }
                return@launch
            }
            if (intent.needsClarification) {
                _uiState.update {
                    it.copy(
                        isNoteLoading = false,
                        noteStatusMessage = intent.clarificationQuestion ?: s(R.string.todo_could_you_clarify)
                    )
                }
                return@launch
            }

            if (intent.intentType != AssistantIntentType.NOTE) {
                _uiState.update {
                    it.copy(isNoteLoading = false, noteStatusMessage = s(R.string.todo_note_capture_hint))
                }
                return@launch
            }

            handleNote(intent.note)
        }
    }

    private suspend fun handleNote(note: NoteRequest?) {
        val content = note?.content?.trim().orEmpty()
        if (content.isBlank()) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isNoteLoading = false,
                    noteStatusMessage = s(R.string.assistant_prompt_say_idea_again)
                )
            }
            return
        }
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val shortTitle = deriveShortTitle(note?.title.orEmpty(), content)
        val sanitized = (note ?: NoteRequest()).copy(title = shortTitle, content = content)
        try {
            noteRepository.insertFromRequest(sanitized, null)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isNoteLoading = false,
                    noteStatusMessage = s(R.string.assistant_note_saved, shortTitle)
                )
            }
        } catch (error: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isNoteLoading = false,
                    noteStatusMessage = s(R.string.assistant_error_save_note_failed)
                )
            }
        }
    }

    fun onDeleteNote(note: NoteItem) {
        viewModelScope.launch {
            noteRepository.deleteNote(note.id)
        }
    }

    private fun deriveShortTitle(title: String, content: String): String {
        val titleWords = title.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val contentWords = content.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val preferred = if (titleWords.size >= 2) titleWords else contentWords
        val shortTitle = preferred.take(3).joinToString(" ")
        return if (shortTitle.isNotBlank()) shortTitle else s(R.string.assistant_default_note_title)
    }

    private suspend fun handleEvent(request: EventRequest?) {
        if (request == null) {
            _uiState.update { it.copy(isLoading = false, statusMessage = s(R.string.assistant_prompt_repeat)) }
            return
        }
        if (request.needsClarification) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    statusMessage = request.clarificationQuestion ?: s(R.string.todo_could_you_clarify)
                )
            }
            return
        }

        val action = request.action
        if (action != EventAction.CREATE) {
            _uiState.update { it.copy(isLoading = false, statusMessage = s(R.string.todo_only_creation_supported)) }
            return
        }

        val derivedTitle = if (request.title.isBlank()) {
            s(R.string.assistant_new_event_title, UUID.randomUUID().toString().take(4))
        } else {
            request.title
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val endDate = now.date.plus(7L, DateTimeUnit.DAY)
        val end = LocalDateTime(endDate, now.time)
        val existingEvents = calendarRepository.getEvents(now, end)
        val conflict = conflictDetector.detectConflicts(request.copy(title = derivedTitle), existingEvents)
        if (conflict.hasConflict) {
            _uiState.update { it.copy(isLoading = false, statusMessage = s(R.string.assistant_conflict, conflict.reasoning)) }
            return
        }

        val finalRequest = request.copy(title = derivedTitle)
        when (val createResult = calendarRepository.createEvent(finalRequest)) {
            is CalendarCreateResult.Success -> {
                _uiState.update { it.copy(isLoading = false, statusMessage = s(R.string.assistant_scheduled, finalRequest.title)) }
                finalRequest.startTime?.let { calendarViewModel.selectDate(it.date) }
                calendarViewModel.refreshEvents()
            }
            is CalendarCreateResult.PermissionDenied -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = s(R.string.assistant_calendar_permission_required)
                    )
                }
            }
            is CalendarCreateResult.NoWritableCalendar -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = s(R.string.assistant_error_no_writable_calendar)
                    )
                }
            }
            is CalendarCreateResult.InvalidInput -> {
                _uiState.update { it.copy(isLoading = false, statusMessage = createResult.reason) }
            }
            is CalendarCreateResult.ProviderError -> {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = s(
                            R.string.assistant_error_schedule_failed,
                            createResult.reason ?: s(R.string.assistant_try_again)
                        )
                    )
                }
            }
        }
    }

    private fun s(@StringRes resId: Int, vararg args: Any): String {
        return appContext.getString(resId, *args)
    }

}

