package com.desk.moodboard.data.ble

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class DeskBleConfigRaw(
    val serviceUuid: String,
    val commandCharacteristicUuid: String,
    val commands: DeskBleCommandsRaw,
)

@Serializable
data class DeskBleCommandsRaw(
    val up: String,
    val down: String,
    val memory1: String,
    val memory2: String,
    val memory3: String,
)

data class DeskBleConfig(
    val serviceUuid: UUID,
    val commandCharacteristicUuid: UUID,
    val commands: DeskBleCommands,
)

data class DeskBleCommands(
    val up: ByteArray,
    val down: ByteArray,
    val memory1: ByteArray,
    val memory2: ByteArray,
    val memory3: ByteArray,
)

class DeskBleConfigException(message: String, cause: Throwable? = null) : Exception(message, cause)
