package com.desk.moodboard.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desk.moodboard.data.model.AwayMessageItem
import com.desk.moodboard.data.model.AwayTranscriptStatus
import com.desk.moodboard.data.repository.AwayMessageRepository
import com.desk.moodboard.voice.AudioRecorder
import com.desk.moodboard.voice.VolcengineASRService
import com.desk.moodboard.voice.VoiceMessageFileStore
import com.desk.moodboard.voice.VoiceMessagePlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.toPersistentList
import java.util.UUID
import kotlinx.coroutines.withTimeoutOrNull

class AwayModeViewModel(
    private val audioRecorder: AudioRecorder,
    private val volcengineASRService: VolcengineASRService,
    private val awayMessageRepository: AwayMessageRepository,
    private val voiceMessageFileStore: VoiceMessageFileStore,
    private val voiceMessagePlayer: VoiceMessagePlayer
) : ViewModel() {

    private val _uiState = MutableStateFlow(AwayModeUiState())
    val uiState: StateFlow<AwayModeUiState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private var transcriptionJob: Job? = null
    @Volatile private var latestTranscript: String? = null

    init {
        observeMessages()
        observePlayback()
    }

    fun toggleAwayMode(isAway: Boolean) {
        if (!isAway) {
            if (_uiState.value.isRecording) {
                stopRecordingAndSave()
            }
            voiceMessagePlayer.stop()
        }
        _uiState.update { it.copy(isAway = isAway) }
    }

    fun updateGreeting(newGreeting: String) {
        val sanitized = newGreeting.trim().ifBlank { "Away.\nLeave a voice mail." }
        _uiState.update { it.copy(customGreeting = sanitized, greetingDraft = sanitized) }
    }

    fun openGreetingEditor() {
        _uiState.update {
            it.copy(
                isGreetingEditorOpen = true,
                greetingDraft = it.customGreeting,
                showDiscardGreetingDialog = false
            )
        }
    }

    fun updateGreetingDraft(draft: String) {
        _uiState.update { it.copy(greetingDraft = draft) }
    }

    fun requestCloseGreetingEditor() {
        _uiState.update { state ->
            if (state.greetingDraft == state.customGreeting) {
                state.copy(
                    isGreetingEditorOpen = false,
                    showDiscardGreetingDialog = false
                )
            } else {
                state.copy(showDiscardGreetingDialog = true)
            }
        }
    }

    fun dismissDiscardGreetingDialog() {
        _uiState.update { it.copy(showDiscardGreetingDialog = false) }
    }

    fun discardGreetingChanges() {
        _uiState.update {
            it.copy(
                isGreetingEditorOpen = false,
                greetingDraft = it.customGreeting,
                showDiscardGreetingDialog = false
            )
        }
    }

    fun saveGreetingDraft() {
        val sanitized = _uiState.value.greetingDraft.trim().ifBlank { "Away.\nLeave a voice mail." }
        _uiState.update {
            it.copy(
                customGreeting = sanitized,
                greetingDraft = sanitized,
                isGreetingEditorOpen = false,
                showDiscardGreetingDialog = false
            )
        }
    }

    fun startRecording() {
        if (_uiState.value.isRecording) return

        try {
            latestTranscript = null
            audioRecorder.startRecording()
            if (!audioRecorder.isRecording.value) {
                throw IllegalStateException("Recorder did not start.")
            }
            volcengineASRService.startStreaming()
            _uiState.update {
                it.copy(
                    isRecording = true,
                    recordingStage = RecordingStage.Recording,
                    errorMessage = null,
                    showSuccessFeedback = false
                )
            }

            recordingJob = viewModelScope.launch {
                audioRecorder.audioChunks.collect { chunk ->
                    volcengineASRService.sendAudioChunk(chunk)
                }
            }

            transcriptionJob = viewModelScope.launch {
                volcengineASRService.transcriptFlow.collect { transcript ->
                    val sanitized = transcript.trim()
                    if (sanitized.isNotBlank()) {
                        latestTranscript = sanitized
                    }
                }
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    errorMessage = "Microphone access failed.",
                    isRecording = false,
                    recordingStage = RecordingStage.Idle
                )
            }
        }
    }

    fun stopRecordingAndSave() {
        if (!_uiState.value.isRecording) return

        recordingJob?.cancel()
        recordingJob = null
        transcriptionJob?.cancel()
        transcriptionJob = null

        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        recordingStage = RecordingStage.Saving,
                        errorMessage = null
                    )
                }

                val timestamp = System.currentTimeMillis()
                val messageId = UUID.randomUUID().toString()
                val pcmData = audioRecorder.stopRecordingRawPcm()
                volcengineASRService.sendAudioChunk(ShortArray(0), isLast = true)

                val audioPath = voiceMessageFileStore.savePcmAsWav(
                    samples = pcmData,
                    timestamp = timestamp
                )

                awayMessageRepository.upsertMessage(
                    AwayMessageItem(
                        id = messageId,
                        timestamp = timestamp,
                        audioFilePath = audioPath,
                        transcribedText = null,
                        transcriptStatus = AwayTranscriptStatus.PENDING
                    )
                )

                _uiState.update {
                    it.copy(
                        recordingStage = RecordingStage.Transcribing,
                        showSuccessFeedback = true
                    )
                }

                val finalTranscript = withTimeoutOrNull(30_000) {
                    volcengineASRService.awaitFinalResult()
                }

                val normalizedTranscript = finalTranscript
                    ?.trim()
                    .orEmpty()
                    .ifBlank { latestTranscript?.trim().orEmpty() }
                if (normalizedTranscript.isBlank()) {
                    awayMessageRepository.updateTranscript(
                        messageId = messageId,
                        transcript = null,
                        status = AwayTranscriptStatus.FAILED
                    )
                } else {
                    awayMessageRepository.updateTranscript(
                        messageId = messageId,
                        transcript = normalizedTranscript,
                        status = AwayTranscriptStatus.READY
                    )
                }

                volcengineASRService.stopStreaming()
                latestTranscript = null
                _uiState.update { it.copy(recordingStage = RecordingStage.Idle) }

                delay(2000)
                _uiState.update { it.copy(showSuccessFeedback = false) }
            } catch (_: Exception) {
                volcengineASRService.stopStreaming()
                latestTranscript = null
                _uiState.update {
                    it.copy(
                        recordingStage = RecordingStage.Idle,
                        errorMessage = "Unable to save this recording.",
                        showSuccessFeedback = false
                    )
                }
            }
        }
    }

    fun onPlayPauseMessage(message: VoiceMessage) {
        val current = _uiState.value
        if (current.activePlaybackId == message.id && current.isPlaying) {
            voiceMessagePlayer.pause()
            return
        }

        if (!voiceMessageFileStore.exists(message.audioFilePath)) {
            _uiState.update {
                it.copy(errorMessage = "Audio file not found.")
            }
            return
        }

        voiceMessagePlayer.play(message.id, message.audioFilePath)
    }

    fun deleteMessage(message: VoiceMessage) {
        viewModelScope.launch {
            try {
                if (_uiState.value.activePlaybackId == message.id) {
                    voiceMessagePlayer.stop()
                }
                awayMessageRepository.deleteMessage(message.id)
                voiceMessageFileStore.delete(message.audioFilePath)
            } catch (_: Exception) {
                _uiState.update { it.copy(errorMessage = "Unable to delete message.") }
            }
        }
    }

    private fun observeMessages() {
        viewModelScope.launch {
            awayMessageRepository.observeMessages().collect { items ->
                _uiState.update { state ->
                    state.copy(
                        messages = items.map { item ->
                            VoiceMessage(
                                id = item.id,
                                timestamp = item.timestamp,
                                audioFilePath = item.audioFilePath,
                                transcribedText = item.transcribedText,
                                transcriptStatus = item.transcriptStatus
                            )
                        }.toPersistentList()
                    )
                }
            }
        }
    }

    private fun observePlayback() {
        viewModelScope.launch {
            voiceMessagePlayer.state.collect { playback ->
                _uiState.update {
                    it.copy(
                        activePlaybackId = playback.activeMessageId,
                        isPlaying = playback.isPlaying,
                        errorMessage = playback.errorMessage
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_uiState.value.isRecording) {
            audioRecorder.stopRecording()
            volcengineASRService.stopStreaming()
        }
        voiceMessagePlayer.release()
        _uiState.update { it.copy(isRecording = false, recordingStage = RecordingStage.Idle) }
    }
}