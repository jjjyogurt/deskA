package com.desk.moodboard.data.ble

import android.content.Context

class DeskBleConfigLoader(
    private val context: Context,
    private val assetFileName: String,
) {
    fun load(): DeskBleConfig {
        try {
            val rawJson = context.assets.open(assetFileName).bufferedReader().use { it.readText() }
            return DeskBleConfigParser.parse(rawJson)
        } catch (error: Exception) {
            throw DeskBleConfigException("Failed to load BLE config from assets.", error)
        }
    }
}
