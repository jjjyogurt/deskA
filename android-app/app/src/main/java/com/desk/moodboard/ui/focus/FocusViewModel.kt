package com.desk.moodboard.ui.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FocusViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

    fun toggleFocus() {
        if (_uiState.value.isRunning) {
            stopTimer()
        } else {
            startTimer()
        }
    }

    private fun startTimer() {
        _uiState.update { it.copy(isRunning = true) }
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _uiState.update { state ->
                    if (state.isCountdownMode) {
                        val newTime = (state.remainingTimeSeconds - 1).coerceAtLeast(0)
                        if (newTime == 0L) {
                            stopTimer()
                        }
                        state.copy(
                            remainingTimeSeconds = newTime,
                            elapsedTimeSeconds = state.elapsedTimeSeconds + 1
                        )
                    } else {
                        state.copy(
                            elapsedTimeSeconds = state.elapsedTimeSeconds + 1
                        )
                    }
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _uiState.update { it.copy(isRunning = false) }
    }

    fun setTimer(minutes: Long) {
        stopTimer()
        _uiState.update {
            it.copy(
                remainingTimeSeconds = minutes * 60,
                isCountdownMode = minutes > 0,
                elapsedTimeSeconds = 0
            )
        }
    }

    fun formatTime(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return "%02d:%02d".format(mins, secs)
    }

    fun getCurrentDisplayTime(): String {
        val state = _uiState.value
        return if (state.isCountdownMode) {
            formatTime(state.remainingTimeSeconds)
        } else {
            formatTime(state.elapsedTimeSeconds)
        }
    }
}

data class FocusUiState(
    val isRunning: Boolean = false,
    val elapsedTimeSeconds: Long = 0,
    val remainingTimeSeconds: Long = 0,
    val isCountdownMode: Boolean = false
)


