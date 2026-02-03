package com.desk.moodboard.data.ble

data class DeskBleDevice(
    val name: String?,
    val address: String,
    val rssi: Int,
)
