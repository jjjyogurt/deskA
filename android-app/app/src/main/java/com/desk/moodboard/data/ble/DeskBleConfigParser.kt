package com.desk.moodboard.data.ble

import java.util.UUID
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object DeskBleConfigParser {
    private val json = Json { ignoreUnknownKeys = false }

    fun parse(rawJson: String): DeskBleConfig {
        try {
            val rawConfig = json.decodeFromString<DeskBleConfigRaw>(rawJson)
            return mapToConfig(rawConfig)
        } catch (error: Exception) {
            throw DeskBleConfigException("Failed to parse BLE config JSON.", error)
        }
    }

    private fun mapToConfig(raw: DeskBleConfigRaw): DeskBleConfig {
        val serviceUuid = parseUuid(raw.serviceUuid, "serviceUuid")
        val commandCharacteristicUuid = parseUuid(raw.commandCharacteristicUuid, "commandCharacteristicUuid")
        val commands = DeskBleCommands(
            up = parseHex(raw.commands.up, "commands.up"),
            down = parseHex(raw.commands.down, "commands.down"),
            stop = parseHex(raw.commands.stop, "commands.stop"),
            memory1 = parseHex(raw.commands.memory1, "commands.memory1"),
            memory2 = parseHex(raw.commands.memory2, "commands.memory2"),
            memory3 = parseHex(raw.commands.memory3, "commands.memory3"),
        )
        return DeskBleConfig(
            serviceUuid = serviceUuid,
            commandCharacteristicUuid = commandCharacteristicUuid,
            commands = commands,
        )
    }

    private fun parseUuid(value: String, fieldName: String): UUID {
        try {
            return UUID.fromString(value)
        } catch (error: Exception) {
            throw DeskBleConfigException("Invalid UUID for $fieldName.", error)
        }
    }

    private fun parseHex(value: String, fieldName: String): ByteArray {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            throw DeskBleConfigException("Hex payload for $fieldName is empty.")
        }
        if (trimmed.length % 2 != 0) {
            throw DeskBleConfigException("Hex payload for $fieldName must have even length.")
        }
        if (!trimmed.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            throw DeskBleConfigException("Hex payload for $fieldName contains non-hex characters.")
        }
        return trimmed.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
