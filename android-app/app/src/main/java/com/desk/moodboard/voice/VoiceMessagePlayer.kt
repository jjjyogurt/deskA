package com.desk.moodboard.voice

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VoicePlaybackState(
    val activeMessageId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    val durationMs: Int = 0,
    val errorMessage: String? = null
)

class VoiceMessagePlayer {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _state = MutableStateFlow(VoicePlaybackState())
    val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    fun play(messageId: String, filePath: String) {
        try {
            val shouldResume = _state.value.activeMessageId == messageId && mediaPlayer != null
            if (!shouldResume) {
                stop()
                val player = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepare()
                    setOnCompletionListener {
                        stop()
                    }
                }
                mediaPlayer = player
                _state.value = VoicePlaybackState(
                    activeMessageId = messageId,
                    isPlaying = false,
                    positionMs = 0,
                    durationMs = player.duration
                )
            }

            mediaPlayer?.start()
            _state.value = _state.value.copy(isPlaying = true, errorMessage = null)
            startProgressUpdates()
        } catch (_: Exception) {
            _state.value = VoicePlaybackState(
                activeMessageId = messageId,
                isPlaying = false,
                positionMs = 0,
                durationMs = 0,
                errorMessage = "Unable to play this message."
            )
            stop()
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        }
        progressJob?.cancel()
        _state.value = _state.value.copy(isPlaying = false, positionMs = player.currentPosition)
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        mediaPlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.stop()
                }
            } catch (_: Exception) {
                // no-op
            } finally {
                player.release()
            }
        }
        mediaPlayer = null
        _state.value = VoicePlaybackState()
    }

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer ?: break
                _state.value = _state.value.copy(
                    positionMs = player.currentPosition,
                    durationMs = player.duration
                )
                delay(250)
            }
        }
    }

    fun release() {
        stop()
    }
}
