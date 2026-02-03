package com.desk.moodboard.domain.desk

sealed class DeskError {
    data object PermissionDenied : DeskError()
    data object BluetoothDisabled : DeskError()
    data object DeviceNotFound : DeskError()
    data object ConfigInvalid : DeskError()
    data class GattError(val message: String) : DeskError()
    data class Unknown(val message: String) : DeskError()
}
