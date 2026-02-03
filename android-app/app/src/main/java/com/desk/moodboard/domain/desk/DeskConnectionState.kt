package com.desk.moodboard.domain.desk

sealed class DeskConnectionState {
    data object Disconnected : DeskConnectionState()
    data object Scanning : DeskConnectionState()
    data object Connecting : DeskConnectionState()
    data class Connected(
        val deviceName: String?,
        val deviceAddress: String,
    ) : DeskConnectionState()
    data class Error(val error: DeskError) : DeskConnectionState()
}
