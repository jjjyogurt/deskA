package com.desk.moodboard.ui.focus

import androidx.compose.runtime.Immutable
import com.desk.moodboard.data.model.AwayTranscriptStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.UUID

@Immutable
enum class RecordingStage {
    Idle,
    Recording,
    Saving,
    Transcribing
}

@Immutable
data class VoiceMessage(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val audioFilePath: String,
    val transcribedText: String? = null,
    val transcriptStatus: AwayTranscriptStatus = AwayTranscriptStatus.PENDING
)

@Immutable
data class AwayModeUiState(
    val isAway: Boolean = false,
    val customGreeting: String = "Away.\nLeave a voice mail.",
    val isRecording: Boolean = false, // maintained for existing call sites
    val recordingStage: RecordingStage = RecordingStage.Idle,
    val isGreetingEditorOpen: Boolean = false,
    val greetingDraft: String = "Away.\nLeave a voice mail.",
    val showDiscardGreetingDialog: Boolean = false,
    val messages: ImmutableList<VoiceMessage> = persistentListOf(),
    val errorMessage: String? = null,
    val showSuccessFeedback: Boolean = false,
    val activePlaybackId: String? = null,
    val isPlaying: Boolean = false
)
