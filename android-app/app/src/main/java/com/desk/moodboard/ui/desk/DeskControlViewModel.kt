package com.desk.moodboard.ui.desk

import android.bluetooth.BluetoothAdapter
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desk.moodboard.data.ble.DeskBleDevice
import com.desk.moodboard.data.ble.DeskBleRepository
import com.desk.moodboard.data.ble.RemoteBleRepository
import com.desk.moodboard.data.ble.RemoteBleBridgeServer
import com.desk.moodboard.domain.desk.DeskCommand
import com.desk.moodboard.domain.desk.DeskConnectionState
import com.desk.moodboard.domain.desk.DeskError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class DeskControlViewModel(
    private val deskRepository: DeskBleRepository,
    private val remoteRepository: RemoteBleRepository,
    private val remoteBridgeServer: RemoteBleBridgeServer,
) : ViewModel() {
    private companion object {
        private const val Tag = "DeskControlVM"
    }
    private enum class MotionState {
        Idle,
        Up,
        Down,
    }

    private val _uiState = MutableStateFlow(DeskControlUiState())
    val uiState: StateFlow<DeskControlUiState> = _uiState
    private var motionState: MotionState = MotionState.Idle

    init {
        viewModelScope.launch {
            deskRepository.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    connectionState = state,
                    isScanning = state is DeskConnectionState.Scanning,
                    error = (state as? DeskConnectionState.Error)?.error,
                )
                if (state is DeskConnectionState.Disconnected || state is DeskConnectionState.Error) {
                    motionState = MotionState.Idle
                }
            }
        }
        viewModelScope.launch {
            deskRepository.scanResults.collect { devices ->
                _uiState.value = _uiState.value.copy(devices = devices)
            }
        }
        viewModelScope.launch {
            remoteRepository.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    remoteConnectionState = state,
                    isRemoteScanning = state is DeskConnectionState.Scanning,
                    error = (state as? DeskConnectionState.Error)?.error,
                )
            }
        }
        viewModelScope.launch {
            remoteRepository.scanResults.collect { devices ->
                _uiState.value = _uiState.value.copy(remoteDevices = devices)
            }
        }
        viewModelScope.launch {
            remoteRepository.remoteCommands.collect { command ->
                if (!isDeskConnected()) {
                    Log.w(Tag, "Remote command dropped because desk is not connected: $command")
                    return@collect
                }
                applyRemoteMotionCommand(command)
            }
        }
    }

    fun updatePermissions(
        hasScanPermission: Boolean,
        hasConnectPermission: Boolean,
        hasAdvertisePermission: Boolean,
        hasLocationPermission: Boolean,
    ) {
        _uiState.value = _uiState.value.copy(
            hasScanPermission = hasScanPermission,
            hasConnectPermission = hasConnectPermission,
            hasAdvertisePermission = hasAdvertisePermission,
            hasLocationPermission = hasLocationPermission,
        )
        syncRemoteBridgeState()
    }

    fun updateBluetoothEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isBluetoothEnabled = enabled)
        syncRemoteBridgeState()
    }

    fun startDeskScan() {
        Log.d(
            Tag,
            "startDeskScan permissions scan=${_uiState.value.hasScanPermission} " +
                "connect=${_uiState.value.hasConnectPermission} " +
                "location=${_uiState.value.hasLocationPermission} " +
                "bluetoothEnabled=${_uiState.value.isBluetoothEnabled}"
        )
        if (!canScan()) {
            _uiState.value = _uiState.value.copy(error = DeskError.PermissionDenied)
            return
        }
        if (!_uiState.value.isBluetoothEnabled) {
            _uiState.value = _uiState.value.copy(error = DeskError.BluetoothDisabled)
            return
        }
        // Keep only one active scanner to reduce UI churn and BLE contention.
        remoteRepository.stopScan()
        deskRepository.startScan().onFailure { error ->
            _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Desk scan failed"))
        }
    }

    fun stopDeskScan() {
        deskRepository.stopScan().onFailure { error ->
            _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Desk stop scan failed"))
        }
    }

    fun startRemoteScan() {
        Log.d(
            Tag,
            "startRemoteScan permissions scan=${_uiState.value.hasScanPermission} " +
                "connect=${_uiState.value.hasConnectPermission} " +
                "location=${_uiState.value.hasLocationPermission} " +
                "bluetoothEnabled=${_uiState.value.isBluetoothEnabled}"
        )
        if (!canScan()) {
            _uiState.value = _uiState.value.copy(error = DeskError.PermissionDenied)
            return
        }
        if (!_uiState.value.isBluetoothEnabled) {
            _uiState.value = _uiState.value.copy(error = DeskError.BluetoothDisabled)
            return
        }
        // Keep only one active scanner to reduce UI churn and BLE contention.
        deskRepository.stopScan()
        remoteRepository.startScan().onFailure { error ->
            _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Remote scan failed"))
        }
    }

    fun stopRemoteScan() {
        remoteRepository.stopScan().onFailure { error ->
            _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Remote stop scan failed"))
        }
    }

    fun selectDeskDevice(device: DeskBleDevice) {
        if (!BluetoothAdapter.checkBluetoothAddress(device.address)) {
            _uiState.value = _uiState.value.copy(error = DeskError.DeviceNotFound)
            return
        }
        _uiState.value = _uiState.value.copy(selectedDevice = device, error = null)
    }

    fun selectRemoteDevice(device: DeskBleDevice) {
        if (!BluetoothAdapter.checkBluetoothAddress(device.address)) {
            _uiState.value = _uiState.value.copy(error = DeskError.DeviceNotFound)
            return
        }
        _uiState.value = _uiState.value.copy(selectedRemoteDevice = device, error = null)
    }

    fun connectSelectedDeskDevice() {
        val selected = _uiState.value.selectedDevice
        if (selected == null) {
            _uiState.value = _uiState.value.copy(error = DeskError.DeviceNotFound)
            return
        }
        if (!BluetoothAdapter.checkBluetoothAddress(selected.address)) {
            _uiState.value = _uiState.value.copy(error = DeskError.DeviceNotFound)
            return
        }
        // Stop both scans before connecting to avoid concurrent scanner pressure.
        deskRepository.stopScan()
        remoteRepository.stopScan()
        deskRepository.connect(selected.address).onFailure { error ->
            _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Desk connect failed"))
        }
    }

    fun connectSelectedRemoteDevice() {
        val selected = _uiState.value.selectedRemoteDevice
        if (selected == null) {
            _uiState.value = _uiState.value.copy(error = DeskError.DeviceNotFound)
            return
        }
        if (!BluetoothAdapter.checkBluetoothAddress(selected.address)) {
            _uiState.value = _uiState.value.copy(error = DeskError.DeviceNotFound)
            return
        }
        // Stop both scans before connecting to avoid concurrent scanner pressure.
        deskRepository.stopScan()
        remoteRepository.stopScan()
        remoteRepository.connect(selected.address).onFailure { error ->
            _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Remote connect failed"))
        }
    }

    fun disconnectDesk() {
        deskRepository.disconnect().onFailure { error ->
            _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Desk disconnect failed"))
        }
    }

    fun disconnectRemote() {
        remoteRepository.disconnect().onFailure { error ->
            _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Remote disconnect failed"))
        }
    }

    fun sendCommand(command: DeskCommand) {
        viewModelScope.launch(Dispatchers.IO) {
            deskRepository.sendCommand(command).onFailure { error ->
                _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Command failed"))
            }
        }
    }

    fun toggleMotion(command: DeskCommand) {
        val targetState = when (command) {
            DeskCommand.Up -> MotionState.Up
            DeskCommand.Down -> MotionState.Down
            else -> return
        }
        val commandToSend = if (motionState == targetState) {
            motionState = MotionState.Idle
            DeskCommand.Stop
        } else {
            motionState = targetState
            command
        }
        viewModelScope.launch(Dispatchers.IO) {
            deskRepository.sendCommand(commandToSend).onFailure { error ->
                _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Command failed"))
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun canScan(): Boolean {
        val state = _uiState.value
        return state.hasScanPermission && state.hasConnectPermission && state.hasLocationPermission
    }

    private fun isDeskConnected(): Boolean {
        return _uiState.value.connectionState is DeskConnectionState.Connected
    }

    private fun syncRemoteBridgeState() {
        val state = _uiState.value
        val shouldRunBridge =
            state.isBluetoothEnabled && state.hasConnectPermission && state.hasAdvertisePermission
        if (shouldRunBridge) {
            remoteBridgeServer.start()
        } else {
            remoteBridgeServer.stop()
        }
    }

    private fun applyRemoteMotionCommand(command: DeskCommand) {
        when (command) {
            DeskCommand.Up -> {
                motionState = MotionState.Up
                sendCommand(DeskCommand.Up)
            }
            DeskCommand.Down -> {
                motionState = MotionState.Down
                sendCommand(DeskCommand.Down)
            }
            DeskCommand.Stop -> {
                motionState = MotionState.Idle
                sendCommand(DeskCommand.Stop)
            }
            is DeskCommand.Memory -> {
                // Remote bridge only forwards motion commands.
            }
        }
    }

    override fun onCleared() {
        motionState = MotionState.Idle
        remoteBridgeServer.stop()
        deskRepository.disconnect()
        remoteRepository.disconnect()
        super.onCleared()
    }
}
