package com.desk.moodboard.ui.desk

import android.bluetooth.BluetoothAdapter
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desk.moodboard.data.ble.DeskBleDevice
import com.desk.moodboard.data.ble.DeskBleRepository
import com.desk.moodboard.domain.desk.DeskCommand
import com.desk.moodboard.domain.desk.DeskConnectionState
import com.desk.moodboard.domain.desk.DeskError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class DeskControlViewModel(
    private val repository: DeskBleRepository,
) : ViewModel() {
    private companion object {
        private const val Tag = "DeskControlVM"
    }
    private val _uiState = MutableStateFlow(DeskControlUiState())
    val uiState: StateFlow<DeskControlUiState> = _uiState

    init {
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                _uiState.value = _uiState.value.copy(
                    connectionState = state,
                    isScanning = state is DeskConnectionState.Scanning,
                    error = (state as? DeskConnectionState.Error)?.error,
                )
            }
        }
        viewModelScope.launch {
            repository.scanResults.collect { devices ->
                _uiState.value = _uiState.value.copy(devices = devices)
            }
        }
    }

    fun updatePermissions(
        hasScanPermission: Boolean,
        hasConnectPermission: Boolean,
        hasLocationPermission: Boolean,
    ) {
        _uiState.value = _uiState.value.copy(
            hasScanPermission = hasScanPermission,
            hasConnectPermission = hasConnectPermission,
            hasLocationPermission = hasLocationPermission,
        )
    }

    fun updateBluetoothEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isBluetoothEnabled = enabled)
    }

    fun startScan() {
        Log.d(
            Tag,
            "startScan permissions scan=${_uiState.value.hasScanPermission} " +
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
        repository.startScan().onFailure { error ->
            _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Scan failed"))
        }
    }

    fun stopScan() {
        repository.stopScan().onFailure { error ->
            _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Stop scan failed"))
        }
    }

    fun selectDevice(device: DeskBleDevice) {
        if (!BluetoothAdapter.checkBluetoothAddress(device.address)) {
            _uiState.value = _uiState.value.copy(error = DeskError.DeviceNotFound)
            return
        }
        _uiState.value = _uiState.value.copy(selectedDevice = device, error = null)
    }

    fun connectSelectedDevice() {
        val selected = _uiState.value.selectedDevice
        if (selected == null) {
            _uiState.value = _uiState.value.copy(error = DeskError.DeviceNotFound)
            return
        }
        if (!BluetoothAdapter.checkBluetoothAddress(selected.address)) {
            _uiState.value = _uiState.value.copy(error = DeskError.DeviceNotFound)
            return
        }
        repository.connect(selected.address).onFailure { error ->
            _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Connect failed"))
        }
    }

    fun disconnect() {
        repository.disconnect().onFailure { error ->
            _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Disconnect failed"))
        }
    }

    fun sendCommand(command: DeskCommand) {
        repository.sendCommand(command).onFailure { error ->
            _uiState.value = _uiState.value.copy(error = DeskError.Unknown(error.message ?: "Command failed"))
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun canScan(): Boolean {
        val state = _uiState.value
        return state.hasScanPermission && state.hasConnectPermission && state.hasLocationPermission
    }
}
