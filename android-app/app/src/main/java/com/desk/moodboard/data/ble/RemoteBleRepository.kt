package com.desk.moodboard.data.ble

import android.util.Log
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import com.desk.moodboard.domain.desk.DeskCommand
import com.desk.moodboard.domain.desk.DeskConnectionState
import com.desk.moodboard.domain.desk.DeskError
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
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
        private const val AudioHeaderSize = 4
        /** Max PCM payload bytes per packet (firmware profile: MTU 256, payload 244). */
        private const val MaxAudioPayloadBytes = 244
        private const val MicStartCommand = 0x10
        private const val MicStopCommand = 0x11
        private const val MtuRequestSize = 256
    }

    private val _connectionState = MutableStateFlow<DeskConnectionState>(DeskConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()
    private val _remoteCommands = MutableSharedFlow<DeskCommand>(extraBufferCapacity = 16)
    val remoteCommands = _remoteCommands.asSharedFlow()
    private val _remoteMicEvents = MutableSharedFlow<RemoteMicEvent>(extraBufferCapacity = 16)
    val remoteMicEvents = _remoteMicEvents.asSharedFlow()
    private val _remoteAudioPackets = MutableSharedFlow<RemoteAudioPacket>(
        extraBufferCapacity = 8192,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val remoteAudioPackets = _remoteAudioPackets.asSharedFlow()
    private var setupState: NotificationSetupState = NotificationSetupState.Idle

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
                        setupState = NotificationSetupState.Idle
                        _connectionState.value = DeskConnectionState.Connected(
                            deviceName = event.device.name,
                            deviceAddress = event.device.address,
                        )
                    }
                    DeskBleClientEvent.Disconnected -> {
                        setupState = NotificationSetupState.Idle
                        _connectionState.value = DeskConnectionState.Disconnected
                    }
                    is DeskBleClientEvent.ServicesDiscovered -> {
                        startNotificationSetup()
                    }
                    DeskBleClientEvent.CommandWritten,
                    is DeskBleClientEvent.DeviceFound -> {
                        // No-op for remote connection tracking.
                    }
                    is DeskBleClientEvent.CharacteristicNotified -> {
                        when (event.characteristicUuid) {
                            BridgeCommandCharacteristicUuid -> {
                                if (event.payload.isEmpty()) {
                                    return@collect
                                }
                                handleCommandNotification(event.payload.first().toInt() and 0xFF)
                            }
                            BridgeAudioCharacteristicUuid -> {
                                parseAudioPacket(event.payload)?.let { packet ->
                                    val emitted = _remoteAudioPackets.tryEmit(packet)
                                    if (!emitted) {
                                        Log.w(
                                            Tag,
                                            "Audio packet dropped seq=${packet.sequence} last=${packet.isLast}"
                                        )
                                    }
                                }
                            }
                            else -> {
                                Log.d(Tag, "Ignoring notify from unknown characteristic=${event.characteristicUuid}")
                            }
                        }
                    }
                    is DeskBleClientEvent.DescriptorWritten -> {
                        handleDescriptorWritten(event)
                    }
                    is DeskBleClientEvent.MtuChanged -> {
                        Log.i(Tag, "Remote BLE MTU negotiated=${event.mtu} status=${event.status}")
                        if (setupState == NotificationSetupState.WaitingForMtu) {
                            if (event.status == BluetoothGatt.GATT_SUCCESS) {
                                requestBle5HighThroughput()
                                setupState = NotificationSetupState.Complete
                            } else {
                                setupState = NotificationSetupState.Failed
                                _connectionState.value = DeskConnectionState.Error(
                                    DeskError.GattError("MTU negotiation failed status=${event.status}")
                                )
                            }
                        }
                    }
                    is DeskBleClientEvent.PhyUpdated -> {
                        Log.i(
                            Tag,
                            "Remote BLE PHY updated txPhy=${event.txPhy} rxPhy=${event.rxPhy} status=${event.status}"
                        )
                    }
                    is DeskBleClientEvent.Error -> {
                        _connectionState.value = DeskConnectionState.Error(DeskError.GattError(event.message))
                    }
                }
            }
        }
    }

    private fun handleCommandNotification(command: Int) {
        when (command) {
            0x00, 0x01, 0x02 -> {
                mapRemoteCommand(command)?.let { mapped ->
                    _remoteCommands.tryEmit(mapped)
                }
            }
            MicStartCommand -> {
                _remoteMicEvents.tryEmit(RemoteMicEvent.Started)
            }
            MicStopCommand -> {
                _remoteMicEvents.tryEmit(RemoteMicEvent.Stopped)
            }
            else -> {
                Log.w(Tag, "Ignoring unknown remote command byte=0x${"%02X".format(command)}")
            }
        }
    }

    private fun startNotificationSetup() {
        if (setupState != NotificationSetupState.Idle) {
            Log.d(Tag, "Skipping setup start; currentState=$setupState")
            return
        }
        setupState = NotificationSetupState.WaitingForCommandDescriptor
        client.enableNotifications(
            serviceUuid = BridgeServiceUuid,
            characteristicUuid = BridgeCommandCharacteristicUuid,
        ).onFailure { error ->
            setupState = NotificationSetupState.Failed
            _connectionState.value = DeskConnectionState.Error(mapError(error))
        }
    }

    /**
     * After MTU is negotiated, request high connection priority and 2M PHY for BLE 5.0 throughput.
     * Failures are logged but do not fail setup (link still works with 1M PHY).
     */
    private fun requestBle5HighThroughput() {
        client.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            .onFailure { e -> Log.w(Tag, "Connection priority request failed: ${e.message}") }
        client.setPreferredPhy(
            BluetoothDevice.PHY_LE_2M_MASK,
            BluetoothDevice.PHY_LE_2M_MASK,
            BluetoothDevice.PHY_OPTION_NO_PREFERRED,
        ).onFailure { e -> Log.w(Tag, "Preferred PHY set failed: ${e.message}") }
    }

    private fun handleDescriptorWritten(event: DeskBleClientEvent.DescriptorWritten) {
        if (event.status != BluetoothGatt.GATT_SUCCESS) {
            setupState = NotificationSetupState.Failed
            _connectionState.value = DeskConnectionState.Error(
                DeskError.GattError(
                    "Descriptor write failed for ${event.characteristicUuid} status=${event.status}"
                )
            )
            return
        }

        when (setupState) {
            NotificationSetupState.WaitingForCommandDescriptor -> {
                if (event.characteristicUuid != BridgeCommandCharacteristicUuid) {
                    return
                }
                setupState = NotificationSetupState.WaitingForAudioDescriptor
                client.enableNotifications(
                    serviceUuid = BridgeServiceUuid,
                    characteristicUuid = BridgeAudioCharacteristicUuid,
                ).onFailure { error ->
                    setupState = NotificationSetupState.Failed
                    _connectionState.value = DeskConnectionState.Error(mapError(error))
                }
            }
            NotificationSetupState.WaitingForAudioDescriptor -> {
                if (event.characteristicUuid != BridgeAudioCharacteristicUuid) {
                    return
                }
                setupState = NotificationSetupState.WaitingForMtu
                client.requestMtu(MtuRequestSize).onFailure { error ->
                    setupState = NotificationSetupState.Failed
                    _connectionState.value = DeskConnectionState.Error(mapError(error))
                }
            }
            NotificationSetupState.WaitingForMtu,
            NotificationSetupState.Complete,
            NotificationSetupState.Failed,
            NotificationSetupState.Idle -> {
                // Ignore late/duplicate descriptor callbacks.
            }
        }
    }

    /**
     * Parse BLE audio packet. Header is always 4 bytes; payload length is variable (legacy 16, BLE 5.0 up to 508).
     */
    private fun parseAudioPacket(payload: ByteArray): RemoteAudioPacket? {
        if (payload.size < AudioHeaderSize) {
            Log.w(Tag, "Ignoring audio packet with invalid size=${payload.size}")
            return null
        }
        if (payload.size > AudioHeaderSize + MaxAudioPayloadBytes) {
            Log.w(Tag, "Ignoring oversized audio packet size=${payload.size} max=${AudioHeaderSize + MaxAudioPayloadBytes}")
            return null
        }
        val sequence = ByteBuffer.wrap(payload, 0, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
        val flags = payload[2].toInt() and 0xFF
        val isLast = (flags and 0x01) == 0x01
        val pcmBytes = payload.copyOfRange(AudioHeaderSize, payload.size)
        return RemoteAudioPacket(
            sequence = sequence,
            isLast = isLast,
            pcmBytes = pcmBytes,
        )
    }

    private fun mapRemoteCommand(command: Int): DeskCommand? {
        return when (command) {
            0x00 -> DeskCommand.Stop
            0x01 -> DeskCommand.Up
            0x02 -> DeskCommand.Down
            else -> null
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

private enum class NotificationSetupState {
    Idle,
    WaitingForCommandDescriptor,
    WaitingForAudioDescriptor,
    WaitingForMtu,
    Complete,
    Failed,
}

sealed class RemoteMicEvent {
    data object Started : RemoteMicEvent()
    data object Stopped : RemoteMicEvent()
}

data class RemoteAudioPacket(
    val sequence: Int,
    val isLast: Boolean,
    val pcmBytes: ByteArray,
)
