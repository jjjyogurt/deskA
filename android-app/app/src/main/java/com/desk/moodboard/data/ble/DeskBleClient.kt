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
import android.os.ParcelUuid
import android.os.SystemClock
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
        private const val ScanReportDelayMs = 500L
        private const val ScanUiUpdateIntervalMs = 400L
    }
    private data class WriteRequest(
        val serviceUuid: UUID,
        val characteristicUuid: UUID,
        val payload: ByteArray,
        val writeType: Int,
    )

    private val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var activeScanCallback: ScanCallback? = null
    private var scanResultsByAddress: Map<String, DeskBleDevice> = emptyMap()
    private var lastScanUiUpdateMs = 0L
    private val writeQueue = ArrayDeque<WriteRequest>()
    private val writeLock = Any()
    private var writeInFlight = false

    private val _scanResults = MutableStateFlow<List<DeskBleDevice>>(emptyList())
    val scanResults = _scanResults.asStateFlow()

    private val _events = MutableSharedFlow<DeskBleClientEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun startScan(
        serviceUuid: UUID? = null,
        deviceNamePrefix: String? = null,
    ): Result<Unit> {
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
            scanResultsByAddress = emptyMap()
            _scanResults.value = emptyList()
            val settings = buildScanSettings()
            val filters = buildScanFilters(serviceUuid = serviceUuid, deviceNamePrefix = deviceNamePrefix)
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

    fun connect(
        address: String,
        forceLeTransport: Boolean = false,
    ): Result<Unit> {
        return runCatching {
            val adapter = bluetoothAdapter ?: throw DeskBleClientException("Bluetooth adapter unavailable.")
            val device = adapter.getRemoteDevice(address)
            Log.d(Tag, "connect address=$address forceLeTransport=$forceLeTransport")
            bluetoothGatt?.close()
            bluetoothGatt = if (forceLeTransport) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        }
    }

    fun disconnect(): Result<Unit> {
        return runCatching {
            clearWriteQueue()
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
            bluetoothGatt = null
        }
    }

    fun writeCommand(
        serviceUuid: UUID,
        characteristicUuid: UUID,
        payload: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
    ): Result<Unit> {
        return runCatching {
            val gatt = bluetoothGatt ?: throw DeskBleClientException("Not connected to a device.")
            enqueueWrite(
                gatt = gatt,
                request = WriteRequest(serviceUuid, characteristicUuid, payload, writeType),
            )
        }
    }

    private fun enqueueWrite(
        gatt: BluetoothGatt,
        request: WriteRequest,
    ) {
        synchronized(writeLock) {
            writeQueue.addLast(request)
            if (writeInFlight) {
                Log.d(Tag, "Write queued size=${writeQueue.size}")
                return
            }
            writeNextLocked(gatt)
        }
    }

    private fun writeNextLocked(gatt: BluetoothGatt) {
        if (writeQueue.isEmpty()) {
            writeInFlight = false
            return
        }
        val next = writeQueue.removeFirst()
        try {
            writeInFlight = true
            val service = gatt.getService(next.serviceUuid)
                ?: throw DeskBleClientException("Service not found.")
            val characteristic = service.getCharacteristic(next.characteristicUuid)
                ?: throw DeskBleClientException("Characteristic not found.")
            writeCharacteristic(gatt, characteristic, next.payload, next.writeType)
            Log.d(Tag, "Write started queueRemaining=${writeQueue.size}")
        } catch (error: Exception) {
            writeInFlight = false
            writeQueue.clear()
            _events.tryEmit(DeskBleClientEvent.Error(error.message ?: "Write failed."))
            throw error
        }
    }

    private fun clearWriteQueue() {
        synchronized(writeLock) {
            writeQueue.clear()
            writeInFlight = false
        }
    }

    private fun createScanCallback(): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResults(listOf(result))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                if (isDebugLoggingEnabled()) {
                    Log.d(Tag, "onBatchScanResults count=${results.size}")
                }
                handleScanResults(results)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(Tag, "onScanFailed errorCode=$errorCode")
                _events.tryEmit(DeskBleClientEvent.Error("Scan failed: $errorCode"))
            }
        }
    }

    private fun handleScanResults(results: List<ScanResult>) {
        val items = results.mapNotNull { toDeskBleDevice(it) }
        if (items.isEmpty()) {
            return
        }
        scanResultsByAddress = items.fold(scanResultsByAddress) { acc, item ->
            acc + (item.address to item)
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastScanUiUpdateMs >= ScanUiUpdateIntervalMs) {
            lastScanUiUpdateMs = now
            _scanResults.value = scanResultsByAddress.values.sortedByDescending { it.rssi }
        }
        items.forEach { item ->
            _events.tryEmit(DeskBleClientEvent.DeviceFound(item))
        }
    }

    private fun toDeskBleDevice(result: ScanResult): DeskBleDevice? {
        if (isDebugLoggingEnabled()) {
            logScanResult(result)
        }
        val device = result.device ?: return null
        val recordName = result.scanRecord?.deviceName
        return DeskBleDevice(
            name = device.name ?: recordName,
            address = device.address,
            rssi = result.rssi,
        )
    }

    private fun isDebugLoggingEnabled(): Boolean {
        return Log.isLoggable(Tag, Log.DEBUG)
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
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
            .setReportDelay(ScanReportDelayMs)
            .build()
    }

    private fun buildScanFilters(
        serviceUuid: UUID?,
        deviceNamePrefix: String?,
    ): List<ScanFilter> {
        val hasServiceUuid = serviceUuid != null
        val hasNamePrefix = !deviceNamePrefix.isNullOrBlank()
        if (!hasServiceUuid && !hasNamePrefix) {
            return emptyList()
        }
        val builder = ScanFilter.Builder()
        if (serviceUuid != null) {
            builder.setServiceUuid(ParcelUuid(serviceUuid))
        }
        if (!deviceNamePrefix.isNullOrBlank()) {
            builder.setDeviceName(deviceNamePrefix)
        }
        return listOf(builder.build())
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
                stopScan()
                _events.tryEmit(DeskBleClientEvent.Connected(gatt.device))
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                clearWriteQueue()
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
            synchronized(writeLock) {
                writeInFlight = false
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    writeQueue.clear()
                    return
                }
                val currentGatt = bluetoothGatt ?: gatt
                if (currentGatt != null) {
                    try {
                        writeNextLocked(currentGatt)
                    } catch (error: Exception) {
                        Log.e(Tag, "Write continuation failed: ${error.message}", error)
                    }
                } else {
                    writeQueue.clear()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
        writeType: Int,
    ) {
        characteristic.writeType = writeType
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
