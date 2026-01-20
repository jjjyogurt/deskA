package com.desk.moodboard.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desk.moodboard.data.model.AssistantIntentType
import com.desk.moodboard.data.model.EventAction
import com.desk.moodboard.data.model.EventRequest
import com.desk.moodboard.data.model.EventType
import com.desk.moodboard.data.model.TodoItem
import com.desk.moodboard.data.model.TodoRequest
import com.desk.moodboard.data.remote.DoubaoService
import com.desk.moodboard.data.repository.CalendarRepository
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
    val isRecording: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val selectedFilter: TodoFilter = TodoFilter.TODAY
)

class TodoViewModel(
    private val doubaoService: DoubaoService?,
    private val todoRepository: TodoRepository,
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

    init {
        viewModelScope.launch {
            todoRepository.observeTodos().collect { items ->
                _uiState.update { it.copy(todos = items) }
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
                    _uiState.update { it.copy(statusMessage = "Couldn't hear anything.") }
                }
            } else {
                _uiState.update { it.copy(isRecording = true, statusMessage = "Listening...") }
                audioRecorder.startRecording()
            }
        }
    }

    private fun processTextInput(text: String) {
        if (doubaoService == null) {
            _uiState.update { it.copy(statusMessage = "API key not configured.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Processing...") }
            val intent = doubaoService.parseAssistantIntent(text)
            if (intent == null) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "I couldn't understand that.") }
                return@launch
            }

            if (intent.needsClarification) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = intent.clarificationQuestion ?: "Could you clarify?"
                    )
                }
                return@launch
            }

            when (intent.intentType) {
                AssistantIntentType.TODO -> handleTodo(intent.todo)
                AssistantIntentType.EVENT -> handleEvent(intent.event)
                AssistantIntentType.CHAT -> {
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = intent.chatResponse ?: "How can I help?")
                    }
                }
            }
        }
    }

    private suspend fun handleTodo(todo: TodoRequest?) {
        if (todo == null || todo.title.isBlank()) {
            _uiState.update { it.copy(isLoading = false, statusMessage = "Please tell me the todo.") }
            return
        }

        todoRepository.insertFromRequest(todo)
        _uiState.update { it.copy(statusMessage = "Added todo: ${todo.title}") }

        if (todo.createCalendarEvent) {
            val date = todo.dueDate
            val time = todo.dueTime
            if (date == null || time == null) {
                _uiState.update { it.copy(isLoading = false, statusMessage = "What time should I add to the calendar?") }
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

    private suspend fun handleEvent(request: EventRequest?) {
        if (request == null) {
            _uiState.update { it.copy(isLoading = false, statusMessage = "Could you repeat that?") }
            return
        }
        if (request.needsClarification) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    statusMessage = request.clarificationQuestion ?: "Could you clarify?"
                )
            }
            return
        }

        val action = request.action
        if (action != EventAction.CREATE) {
            _uiState.update { it.copy(isLoading = false, statusMessage = "Only creation is supported here.") }
            return
        }

        val derivedTitle = if (request.title.isBlank()) {
            "New event ${UUID.randomUUID().toString().take(4)}"
        } else {
            request.title
        }

        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val endDate = now.date.plus(7L, DateTimeUnit.DAY)
        val end = LocalDateTime(endDate, now.time)
        val existingEvents = calendarRepository.getEvents(now, end)
        val conflict = conflictDetector.detectConflicts(request.copy(title = derivedTitle), existingEvents)
        if (conflict.hasConflict) {
            _uiState.update { it.copy(isLoading = false, statusMessage = "Conflict: ${conflict.reasoning}") }
            return
        }

        val finalRequest = request.copy(title = derivedTitle)
        if (calendarRepository.createEvent(finalRequest)) {
            _uiState.update { it.copy(isLoading = false, statusMessage = "Scheduled: ${finalRequest.title}") }
            finalRequest.startTime?.let { calendarViewModel.selectDate(it.date) }
            calendarViewModel.refreshEvents()
        } else {
            _uiState.update { it.copy(isLoading = false, statusMessage = "Failed to schedule.") }
        }
    }

}

