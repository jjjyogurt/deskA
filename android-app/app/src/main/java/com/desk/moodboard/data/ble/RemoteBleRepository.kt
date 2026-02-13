package com.desk.moodboard.data.ble

import android.util.Log
import com.desk.moodboard.domain.desk.DeskCommand
import com.desk.moodboard.domain.desk.DeskConnectionState
import com.desk.moodboard.domain.desk.DeskError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class RemoteBleRepository(
    private val client: DeskBleClient,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private companion object {
        private const val Tag = "RemoteBleRepository"
        private const val RemoteDeviceName = "ESP32S3_Remote_Desk"
    }

    private val _connectionState = MutableStateFlow<DeskConnectionState>(DeskConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()
    private val _remoteCommands = MutableSharedFlow<DeskCommand>(extraBufferCapacity = 16)
    val remoteCommands = _remoteCommands.asSharedFlow()

    val scanResults = client.scanResults

    init {
        observeClientEvents()
    }

    fun startScan(): Result<Unit> {
        Log.d(Tag, "startScan requested")
        return client.startScan(
            serviceUuid = BridgeServiceUuid,
            deviceNamePrefix = RemoteDeviceName,
        ).onFailure { error ->
            _connectionState.value = DeskConnectionState.Error(mapError(error))
        }
    }

    fun stopScan(): Result<Unit> {
        return client.stopScan().onFailure { error ->
            _connectionState.value = DeskConnectionState.Error(mapError(error))
        }
    }

    fun connect(address: String): Result<Unit> {
        // Remote ESP32 has shown intermittent GATT 133 on some devices.
        // Force LE transport for a more deterministic BLE-only link.
        return client.connect(address, forceLeTransport = true).onFailure { error ->
            _connectionState.value = DeskConnectionState.Error(mapError(error))
        }
    }

    fun disconnect(): Result<Unit> {
        return client.disconnect().onFailure { error ->
            _connectionState.value = DeskConnectionState.Error(mapError(error))
        }
    }

    private fun observeClientEvents() {
        coroutineScope.launch {
            client.events.collect { event ->
                when (event) {
                    DeskBleClientEvent.ScanStarted -> {
                        _connectionState.value = DeskConnectionState.Scanning
                    }
                    DeskBleClientEvent.ScanStopped -> {
                        if (_connectionState.value is DeskConnectionState.Scanning) {
                            _connectionState.value = DeskConnectionState.Disconnected
                        }
                    }
                    is DeskBleClientEvent.Connected -> {
                        _connectionState.value = DeskConnectionState.Connected(
                            deviceName = event.device.name,
                            deviceAddress = event.device.address,
                        )
                    }
                    DeskBleClientEvent.Disconnected -> {
                        _connectionState.value = DeskConnectionState.Disconnected
                    }
                    is DeskBleClientEvent.ServicesDiscovered -> {
                        client.enableNotifications(
                            serviceUuid = BridgeServiceUuid,
                            characteristicUuid = BridgeCommandCharacteristicUuid,
                        ).onFailure { error ->
                            _connectionState.value = DeskConnectionState.Error(mapError(error))
                        }
                    }
                    DeskBleClientEvent.CommandWritten,
                    is DeskBleClientEvent.DeviceFound -> {
                        // No-op for remote connection tracking.
                    }
                    is DeskBleClientEvent.CharacteristicNotified -> {
                        if (event.characteristicUuid != BridgeCommandCharacteristicUuid || event.payload.isEmpty()) {
                            return@collect
                        }
                        mapRemoteCommand(event.payload.first().toInt() and 0xFF)?.let { command ->
                            _remoteCommands.tryEmit(command)
                        }
                    }
                    is DeskBleClientEvent.Error -> {
                        _connectionState.value = DeskConnectionState.Error(DeskError.GattError(event.message))
                    }
                }
            }
        }
    }

    private fun mapRemoteCommand(command: Int): DeskCommand? {
        return when (command) {
            0x00 -> DeskCommand.Stop
            0x01 -> DeskCommand.Up
            0x02 -> DeskCommand.Down
            else -> {
                Log.w(Tag, "Ignoring unknown remote command byte=0x${"%02X".format(command)}")
                null
            }
        }
    }

    private fun mapError(error: Throwable): DeskError {
        val message = error.message ?: "Unknown error"
        return when (error) {
            is DeskBleClientException -> DeskError.GattError(message)
            else -> DeskError.Unknown(message)
        }
    }
}
