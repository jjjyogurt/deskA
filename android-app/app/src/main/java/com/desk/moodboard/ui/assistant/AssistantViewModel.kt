package com.desk.moodboard.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desk.moodboard.data.model.*
import com.desk.moodboard.data.remote.DoubaoService
import com.desk.moodboard.data.repository.CalendarRepository
import com.desk.moodboard.domain.ConflictDetector
import com.desk.moodboard.ui.home.CalendarViewModel
import com.desk.moodboard.voice.AudioRecorder
import com.desk.moodboard.voice.VolcengineASRService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val audioRecorder: AudioRecorder,
    private val volcengineASRService: VolcengineASRService,
    private val conflictDetector: ConflictDetector,
    private val calendarViewModel: CalendarViewModel
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState

    private var asrInitialized = false
    private var recordingJob: Job? = null

    init {
        addMessage("Hi! I'm your AI calendar assistant. What would you like to do?", false)
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
                // STOP recording
                _uiState.value = _uiState.value.copy(isRecording = false, isLoading = true)
                audioRecorder.stopRecording()
                volcengineASRService.sendAudioChunk(ShortArray(0), isLast = true)
                
                // Wait briefly for final result
                delay(1000)
                
                val finalTranscript = _uiState.value.currentTranscript
                _uiState.value = _uiState.value.copy(isLoading = false, currentTranscript = "")
                
                recordingJob?.cancel()
                volcengineASRService.stopStreaming()
                
                if (finalTranscript.isNotBlank()) {
                    addMessage(finalTranscript, true)
                    processTextInput(finalTranscript)
                } else {
                    addMessage("Couldn't hear anything.", false)
                }
            } else {
                // START recording
                _uiState.value = _uiState.value.copy(isRecording = true, currentTranscript = "")
                
                volcengineASRService.startStreaming(viewModelScope)

                audioRecorder.startRecording()

                // Pipe audio chunks to ASR and collect transcript
                recordingJob = viewModelScope.launch {
                    launch {
                        audioRecorder.audioChunks.collect { chunk ->
                            volcengineASRService.sendAudioChunk(chunk)
                        }
                    }
                    launch {
                        volcengineASRService.transcriptFlow.collect { transcript ->
                            _uiState.value = _uiState.value.copy(currentTranscript = transcript)
                        }
                    }
                }
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
            val request = doubaoService.parseNaturalLanguage(text)
            
            if (request != null) {
                handleEventRequest(request)
            } else {
                addMessage("I couldn't understand that. Could you rephrase?", false)
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private suspend fun handleEventRequest(request: EventRequest) {
        when (request.action) {
            EventAction.CHAT -> {
                addMessage(request.chatResponse ?: "How can I help you?", false)
            }
            EventAction.CREATE -> {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val existingEvents = calendarRepository.getEvents(now, now.plus(7, DateTimeUnit.DAY))
                val conflict = conflictDetector.detectConflicts(request, existingEvents)
                
                if (conflict.hasConflict) {
                    addMessage("Conflict: ${conflict.reasoning}", false)
                } else {
                    if (calendarRepository.createEvent(request)) {
                        addMessage("Scheduled: ${request.title}", false)
                        request.startTime?.let { calendarViewModel.selectDate(it.date) }
                        calendarViewModel.refreshEvents()
                    } else {
                        addMessage("Failed to schedule.", false)
                    }
                }
            }
            EventAction.LIST -> {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val events = calendarRepository.getEvents(now, now.plus(1, DateTimeUnit.DAY))
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

    private fun LocalDateTime.plus(value: Int, unit: DateTimeUnit): LocalDateTime {
        return this.toInstant(TimeZone.currentSystemDefault()).plus(value, unit, TimeZone.currentSystemDefault()).toLocalDateTime(TimeZone.currentSystemDefault())
    }
}
