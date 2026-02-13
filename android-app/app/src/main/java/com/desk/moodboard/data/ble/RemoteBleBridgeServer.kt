package com.desk.moodboard.data.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.desk.moodboard.domain.desk.DeskCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

class RemoteBleBridgeServer(
    private val context: Context,
    private val deskRepository: DeskBleRepository,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private companion object {
        private const val Tag = "RemoteBleBridgeServer"
        private const val CommandStop = 0x00
        private const val CommandUp = 0x01
        private const val CommandDown = 0x02
        private const val AdvertiseErrorDataTooLarge = 1
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter
    private val advertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapter?.bluetoothLeAdvertiser

    private var gattServer: BluetoothGattServer? = null
    private var isAdvertising = false

    fun start() {
        runCatching {
            if (!hasRequiredRuntimePermissions()) {
                Log.w(Tag, "Bridge server not started: missing BLE runtime permissions.")
                return
            }
            val adapter = bluetoothAdapter
            if (adapter == null || !adapter.isEnabled) {
                Log.w(Tag, "Bridge server not started: Bluetooth adapter unavailable or disabled.")
                return
            }
            if (gattServer != null) {
                return
            }
            val server = bluetoothManager?.openGattServer(context, gattServerCallback)
                ?: throw IllegalStateException("Unable to open BLE GATT server.")
            gattServer = server
            server.addService(buildBridgeService())
            startAdvertising()
            Log.i(
                Tag,
                "Bridge server started service=${BridgeServiceUuid} characteristic=${BridgeCommandCharacteristicUuid}"
            )
        }.onFailure { error ->
            Log.e(Tag, "Failed to start bridge server.", error)
        }
    }

    fun stop() {
        runCatching {
            stopAdvertising()
            gattServer?.clearServices()
            gattServer?.close()
            gattServer = null
            Log.i(Tag, "Bridge server stopped.")
        }.onFailure { error ->
            Log.e(Tag, "Failed to stop bridge server.", error)
        }
    }

    private fun startAdvertising() {
        if (isAdvertising) {
            return
        }
        val bleAdvertiser = advertiser ?: run {
            Log.w(Tag, "BLE advertiser unavailable; cannot advertise bridge service.")
            return
        }
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            // Keep payload minimal to avoid ADVERTISE_FAILED_DATA_TOO_LARGE on OEM stacks.
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(BridgeServiceUuid))
            .build()
        bleAdvertiser.startAdvertising(settings, data, advertiseCallback)
    }

    private fun stopAdvertising() {
        if (!isAdvertising) {
            return
        }
        advertiser?.stopAdvertising(advertiseCallback)
        isAdvertising = false
    }

    private fun buildBridgeService(): BluetoothGattService {
        val service = BluetoothGattService(
            BridgeServiceUuid,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )
        val commandCharacteristic = BluetoothGattCharacteristic(
            BridgeCommandCharacteristicUuid,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        service.addCharacteristic(commandCharacteristic)
        return service
    }

    private fun hasRequiredRuntimePermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }
        val hasConnect = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT,
        ) == PackageManager.PERMISSION_GRANTED
        val hasAdvertise = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_ADVERTISE,
        ) == PackageManager.PERMISSION_GRANTED
        return hasConnect && hasAdvertise
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
            Log.i(Tag, "Bridge advertising started.")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            if (errorCode == AdvertiseErrorDataTooLarge) {
                Log.e(Tag, "Bridge advertising failed: payload too large (errorCode=1).")
            } else {
                Log.e(Tag, "Bridge advertising failed errorCode=$errorCode")
            }
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(Tag, "Bridge peer state change address=${device.address} status=$status newState=$newState")
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            val payloadHex = value.joinToString(separator = " ") { byte -> "%02X".format(byte) }
            Log.i(
                Tag,
                "Received remote command from=${device.address} " +
                    "char=${characteristic.uuid} bytes=[$payloadHex] prepared=$preparedWrite offset=$offset"
            )
            if (characteristic.uuid != BridgeCommandCharacteristicUuid || offset != 0 || value.isEmpty()) {
                Log.w(
                    Tag,
                    "Ignoring invalid remote write char=${characteristic.uuid} offset=$offset size=${value.size}"
                )
            } else {
                forwardCommandToDesk(value.first().toInt() and 0xFF)
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }
    }

    private fun forwardCommandToDesk(command: Int) {
        val deskCommand = when (command) {
            CommandStop -> {
                Log.i(Tag, "Decoded remote command=STOP (0x00)")
                DeskCommand.Stop
            }
            CommandUp -> {
                Log.i(Tag, "Decoded remote command=UP (0x01)")
                DeskCommand.Up
            }
            CommandDown -> {
                Log.i(Tag, "Decoded remote command=DOWN (0x02)")
                DeskCommand.Down
            }
            else -> {
                Log.w(Tag, "Decoded remote command=UNKNOWN (0x${"%02X".format(command)})")
                return
            }
        }
        coroutineScope.launch {
            deskRepository.sendCommand(deskCommand).onFailure { error ->
                Log.e(Tag, "Failed to forward remote command=$deskCommand", error)
            }
        }
    }
}

val BridgeServiceUuid: UUID = UUID.fromString("c1f0d8a0-8b1b-4c0b-9e1c-2c1f7f2b2c11")
val BridgeCommandCharacteristicUuid: UUID = UUID.fromString("c1f0d8a1-8b1b-4c0b-9e1c-2c1f7f2b2c11")
