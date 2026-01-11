package com.desk.moodboard.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desk.moodboard.data.model.*
import com.desk.moodboard.data.remote.DoubaoService
import com.desk.moodboard.data.repository.CalendarRepository
import com.desk.moodboard.domain.ConflictDetector
import com.desk.moodboard.security.SecureKeyManager
import com.desk.moodboard.ui.home.CalendarViewModel
import com.desk.moodboard.voice.VoiceProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.*
import android.content.Context
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isRecording: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

class AssistantViewModel(
    private val doubaoService: DoubaoService?,
    private val calendarRepository: CalendarRepository,
    private val voiceProcessor: VoiceProcessor,
    private val conflictDetector: ConflictDetector,
    private val calendarViewModel: CalendarViewModel
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState

    init {
        addMessage("Hi! I'm your AI calendar assistant. What would you like to do?", false)
    }

    fun onSendMessage(text: String) {
        if (text.isBlank()) return
        
        android.util.Log.d("AssistantVM", "onSendMessage: $text")
        addMessage(text, true)
        processTextInput(text)
    }

    fun onToggleRecording(context: Context) {
        if (_uiState.value.isRecording) {
            android.util.Log.d("AssistantVM", "Stopping recording...")
            val file = voiceProcessor.stopRecording()
            _uiState.value = _uiState.value.copy(isRecording = false, isLoading = true)
            if (file != null) {
                android.util.Log.d("AssistantVM", "Audio file captured: ${file.absolutePath}, size: ${file.length()}")
                viewModelScope.launch {
                    val text = voiceProcessor.processAudio(file)
                    if (text != null) {
                        android.util.Log.d("AssistantVM", "Transcribed text: $text")
                        addMessage(text, true)
                        processTextInput(text)
                    } else {
                        android.util.Log.e("AssistantVM", "Transcription returned null")
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "Failed to transcribe audio")
                    }
                }
            } else {
                android.util.Log.e("AssistantVM", "Audio file is null after stopping recording")
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        } else {
            android.util.Log.d("AssistantVM", "Starting recording...")
            voiceProcessor.startRecording(context)
            _uiState.value = _uiState.value.copy(isRecording = true)
        }
    }

    private fun processTextInput(text: String) {
        if (doubaoService == null) {
            android.util.Log.e("AssistantVM", "DoubaoService is null!")
            addMessage("Doubao API key is not configured.", false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            android.util.Log.d("AssistantVM", "Calling parseNaturalLanguage for: $text")
            val request = doubaoService.parseNaturalLanguage(text)
            
            if (request != null) {
                android.util.Log.d("AssistantVM", "Successfully parsed request: $request")
                handleEventRequest(request)
            } else {
                android.util.Log.w("AssistantVM", "Failed to parse request for text: $text")
                addMessage("I'm sorry, I couldn't understand that. Could you rephrase?", false)
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private suspend fun handleEventRequest(request: EventRequest) {
        when (request.action) {
            EventAction.CHAT -> {
                addMessage(request.chatResponse ?: "How can I help you today?", false)
            }
            EventAction.CREATE -> {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val existingEvents = calendarRepository.getEvents(
                    now, 
                    now.toInstant(TimeZone.currentSystemDefault()).plus(7, DateTimeUnit.DAY, TimeZone.currentSystemDefault()).toLocalDateTime(TimeZone.currentSystemDefault())
                )
                val conflict = conflictDetector.detectConflicts(request, existingEvents)
                
                if (conflict.hasConflict) {
                    addMessage("Conflict detected: ${conflict.reasoning}. Should I schedule it anyway?", false)
                } else {
                    val success = calendarRepository.createEvent(request)
                    if (success) {
                        addMessage("Successfully scheduled: ${request.title}", false)
                        request.startTime?.let { 
                            calendarViewModel.selectDate(it.date)
                        }
                        calendarViewModel.refreshEvents() // Added this line
                    } else {
                        addMessage("Failed to schedule event.", false)
                    }
                }
            }
            EventAction.LIST -> {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val end = now.toInstant(TimeZone.currentSystemDefault()).plus(1, DateTimeUnit.DAY, TimeZone.currentSystemDefault()).toLocalDateTime(TimeZone.currentSystemDefault())
                val events = calendarRepository.getEvents(now, end)
                if (events.isEmpty()) {
                    addMessage("You have no events scheduled for today.", false)
                } else {
                    val list = events.joinToString("\n") { "- ${it.title} at ${it.startTime.time}" }
                    addMessage("Your events for today:\n$list", false)
                }
            }
            else -> addMessage("Action ${request.action} is not yet supported.", false)
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        val newMessage = ChatMessage(UUID.randomUUID().toString(), text, isUser)
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + newMessage
        )
    }
}

