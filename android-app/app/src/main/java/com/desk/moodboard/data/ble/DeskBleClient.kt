package com.desk.moodboard.data.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class DeskBleClient(
    private val context: Context,
) {
    private companion object {
        private const val Tag = "DeskBleClient"
    }
    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var activeScanCallback: ScanCallback? = null

    private val _scanResults = MutableStateFlow<List<DeskBleDevice>>(emptyList())
    val scanResults = _scanResults.asStateFlow()

    private val _events = MutableSharedFlow<DeskBleClientEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun startScan(): Result<Unit> {
        return runCatching {
            Log.d(Tag, "startScan() called")
            val adapter = bluetoothAdapter ?: throw DeskBleClientException("Bluetooth adapter unavailable.")
            Log.d(Tag, "Bluetooth enabled=${adapter.isEnabled}")
            if (!adapter.isEnabled) {
                throw DeskBleClientException("Bluetooth is disabled.")
            }
            val bleScanner = scanner ?: throw DeskBleClientException("BLE scanner unavailable (null).")
            Log.d(Tag, "bleScanner=$bleScanner")
            activeScanCallback?.let { existing ->
                Log.d(Tag, "Stopping previous scan before starting a new one")
                bleScanner.stopScan(existing)
                activeScanCallback = null
            }
            val callback = createScanCallback()
            activeScanCallback = callback
            _scanResults.value = emptyList()
            val settings = buildScanSettings()
            val filters = emptyList<ScanFilter>()
            bleScanner.startScan(filters, settings, callback)
            Log.d(Tag, "BLE scan started")
            _events.tryEmit(DeskBleClientEvent.ScanStarted)
            Unit
        }.onFailure { error ->
            Log.e(Tag, "startScan() failed: ${error.message}", error)
        }
    }

    fun stopScan(): Result<Unit> {
        return runCatching {
            val callback = activeScanCallback ?: run {
                Log.d(Tag, "stopScan() called but no active scan")
                return@runCatching
            }
            scanner?.stopScan(callback)
            activeScanCallback = null
            Log.d(Tag, "BLE scan stopped")
            _events.tryEmit(DeskBleClientEvent.ScanStopped)
            Unit
        }.onFailure { error ->
            Log.e(Tag, "stopScan() failed: ${error.message}", error)
        }
    }

    fun connect(address: String): Result<Unit> {
        return runCatching {
            val adapter = bluetoothAdapter ?: throw DeskBleClientException("Bluetooth adapter unavailable.")
            val device = adapter.getRemoteDevice(address)
            bluetoothGatt?.close()
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        }
    }

    fun disconnect(): Result<Unit> {
        return runCatching {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        }
    }

    fun writeCommand(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        payload: ByteArray,
    ): Result<Unit> {
        return runCatching {
            val gatt = bluetoothGatt ?: throw DeskBleClientException("Not connected to a device.")
            val service = gatt.getService(serviceUuid)
                ?: throw DeskBleClientException("Service not found.")
            val characteristic = service.getCharacteristic(characteristicUuid)
                ?: throw DeskBleClientException("Characteristic not found.")
            writeCharacteristic(gatt, characteristic, payload)
        }
    }

    private fun createScanCallback(): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                Log.d(Tag, "onBatchScanResults count=${results.size}")
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(Tag, "onScanFailed errorCode=$errorCode")
                _events.tryEmit(DeskBleClientEvent.Error("Scan failed: $errorCode"))
            }
        }
    }

    private fun handleScanResult(result: ScanResult) {
        logScanResult(result)
        val device = result.device ?: return
        val recordName = result.scanRecord?.deviceName
        val item = DeskBleDevice(
            name = device.name ?: recordName,
            address = device.address,
            rssi = result.rssi,
        )
        val updated = _scanResults.value
            .filterNot { it.address == item.address }
            .plus(item)
            .sortedByDescending { it.rssi }
        _scanResults.value = updated
        _events.tryEmit(DeskBleClientEvent.DeviceFound(item))
    }

    private fun logScanResult(result: ScanResult) {
        val device = result.device ?: return
        val record = result.scanRecord
        val recordName = record?.deviceName ?: "null"
        val serviceUuids = record?.serviceUuids
            ?.joinToString { it.uuid.toString() }
            ?: "none"
        Log.d(
            Tag,
            "ScanResult name=${device.name} recordName=$recordName " +
                "addr=${device.address} rssi=${result.rssi} services=$serviceUuids"
        )
    }

    private fun buildScanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(Tag, "GATT state change status=$status newState=$newState")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _events.tryEmit(DeskBleClientEvent.Error("GATT error: $status"))
                gatt.close()
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _events.tryEmit(DeskBleClientEvent.Connected(gatt.device))
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _events.tryEmit(DeskBleClientEvent.Disconnected)
                gatt.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(Tag, "Services discovered status=$status count=${gatt.services.size}")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _events.tryEmit(DeskBleClientEvent.ServicesDiscovered(gatt.services))
            } else {
                _events.tryEmit(DeskBleClientEvent.Error("Service discovery failed: $status"))
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            Log.d(Tag, "Characteristic write uuid=${characteristic.uuid} status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _events.tryEmit(DeskBleClientEvent.CommandWritten)
            } else {
                _events.tryEmit(DeskBleClientEvent.Error("Write failed: $status"))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
    ) {
        characteristic.value = payload
        val initiated = gatt.writeCharacteristic(characteristic)
        if (!initiated) {
            throw DeskBleClientException("Characteristic write could not be initiated.")
        }
    }
}

sealed class DeskBleClientEvent {
    data object ScanStarted : DeskBleClientEvent()
    data object ScanStopped : DeskBleClientEvent()
    data class DeviceFound(val device: DeskBleDevice) : DeskBleClientEvent()
    data class Connected(val device: BluetoothDevice) : DeskBleClientEvent()
    data object Disconnected : DeskBleClientEvent()
    data class ServicesDiscovered(val services: List<BluetoothGattService>) : DeskBleClientEvent()
    data object CommandWritten : DeskBleClientEvent()
    data class Error(val message: String) : DeskBleClientEvent()
}

class DeskBleClientException(message: String) : Exception(message)
