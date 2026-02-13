package com.desk.moodboard.ui.desk

import com.desk.moodboard.data.ble.DeskBleDevice
import com.desk.moodboard.domain.desk.DeskConnectionState
import com.desk.moodboard.domain.desk.DeskError

data class DeskControlUiState(
    val connectionState: DeskConnectionState = DeskConnectionState.Disconnected,
    val devices: List<DeskBleDevice> = emptyList(),
    val selectedDevice: DeskBleDevice? = null,
    val isScanning: Boolean = false,
    val remoteConnectionState: DeskConnectionState = DeskConnectionState.Disconnected,
    val remoteDevices: List<DeskBleDevice> = emptyList(),
    val selectedRemoteDevice: DeskBleDevice? = null,
    val isRemoteScanning: Boolean = false,
    val hasScanPermission: Boolean = true,
    val hasConnectPermission: Boolean = true,
    val hasAdvertisePermission: Boolean = true,
    val hasLocationPermission: Boolean = true,
    val isBluetoothEnabled: Boolean = true,
    val error: DeskError? = null,
)
