package com.desk.moodboard.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desk.moodboard.data.preferences.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val userPreferences: UserPreferences,
) : ViewModel() {
    val eInkEnabled: StateFlow<Boolean> = userPreferences.eInkMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = false,
    )

    fun setEInk(enabled: Boolean) {
        viewModelScope.launch {
            userPreferences.setEInkMode(enabled)
        }
    }
}
