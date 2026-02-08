package com.desk.moodboard.data.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import com.desk.moodboard.domain.desk.DeskCommand
import com.desk.moodboard.domain.desk.DeskConnectionState
import com.desk.moodboard.domain.desk.DeskError
import com.desk.moodboard.domain.desk.DeskMemorySlot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class DeskBleRepository(
    private val configLoader: DeskBleConfigLoader,
    private val client: DeskBleClient,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private companion object {
        private const val Tag = "DeskBleRepository"
    }
    private val _connectionState = MutableStateFlow<DeskConnectionState>(DeskConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    val scanResults = client.scanResults

    private val _config = MutableStateFlow<DeskBleConfig?>(null)

    init {
        observeClientEvents()
        loadConfig()
    }

    fun startScan(): Result<Unit> {
        Log.d(Tag, "startScan requested")
        return client.startScan().onFailure { error ->
            _connectionState.value = DeskConnectionState.Error(mapError(error))
        }
    }

    fun stopScan(): Result<Unit> {
        return client.stopScan().onFailure { error ->
            _connectionState.value = DeskConnectionState.Error(mapError(error))
        }
    }

    fun connect(address: String): Result<Unit> {
        return client.connect(address).onFailure { error ->
            _connectionState.value = DeskConnectionState.Error(mapError(error))
        }
    }

    fun disconnect(): Result<Unit> {
        return client.disconnect().onFailure { error ->
            _connectionState.value = DeskConnectionState.Error(mapError(error))
        }
    }

    fun sendCommand(command: DeskCommand): Result<Unit> {
        val config = _config.value ?: return runCatching {
            loadConfig().getOrThrow()
        }.fold(
            onSuccess = { sendCommand(command) },
            onFailure = { error ->
                _connectionState.value = DeskConnectionState.Error(mapError(error))
                Result.failure(error)
            },
        )
        val payload = when (command) {
            DeskCommand.Up -> config.commands.up
            DeskCommand.Down -> config.commands.down
            DeskCommand.Stop -> config.commands.stop
            is DeskCommand.Memory -> mapMemoryCommand(config, command.slot)
        }
        val writeType = when (command) {
            DeskCommand.Up,
            DeskCommand.Down,
            DeskCommand.Stop -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            is DeskCommand.Memory -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        return client.writeCommand(
            serviceUuid = config.serviceUuid,
            characteristicUuid = config.commandCharacteristicUuid,
            payload = payload,
            writeType = writeType,
        ).onFailure { error ->
            _connectionState.value = DeskConnectionState.Error(mapError(error))
        }
    }

    fun loadConfig(): Result<Unit> {
        return runCatching {
            val config = configLoader.load()
            _config.value = config
        }.onFailure { error ->
            _connectionState.value = DeskConnectionState.Error(DeskError.ConfigInvalid)
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
                    DeskBleClientEvent.CommandWritten -> {
                        // Keep state as-is on successful write.
                    }
                    is DeskBleClientEvent.ServicesDiscovered -> {
                        // Keep state as-is; repository handles commands via config.
                    }
                    is DeskBleClientEvent.DeviceFound -> {
                        // scanResults flow already updated by client.
                    }
                    is DeskBleClientEvent.Error -> {
                        _connectionState.value = DeskConnectionState.Error(DeskError.GattError(event.message))
                    }
                }
            }
        }
    }

    private fun mapMemoryCommand(
        config: DeskBleConfig,
        slot: DeskMemorySlot,
    ): ByteArray {
        return when (slot) {
            DeskMemorySlot.One -> config.commands.memory1
            DeskMemorySlot.Two -> config.commands.memory2
            DeskMemorySlot.Three -> config.commands.memory3
        }
    }

    private fun mapError(error: Throwable): DeskError {
        val message = error.message ?: "Unknown error"
        return when (error) {
            is DeskBleConfigException -> DeskError.ConfigInvalid
            is DeskBleClientException -> DeskError.GattError(message)
            else -> DeskError.Unknown(message)
        }
    }
}
