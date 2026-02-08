package com.desk.moodboard.data.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeskBleConfigParserTest {
    @Test
    fun parse_validJson_returnsConfig() {
        val json = """
            {
              "serviceUuid": "0000fff0-0000-1000-8000-00805f9b34fb",
              "commandCharacteristicUuid": "0000fff1-0000-1000-8000-00805f9b34fb",
              "commands": {
                "up": "01",
                "down": "02",
                "stop": "00",
                "memory1": "11",
                "memory2": "12",
                "memory3": "13"
              }
            }
        """.trimIndent()

        val config = DeskBleConfigParser.parse(json)

        assertEquals("0000fff0-0000-1000-8000-00805f9b34fb", config.serviceUuid.toString())
        assertEquals(1, config.commands.up.size)
        assertEquals(0x01.toByte(), config.commands.up[0])
    }

    @Test
    fun parse_invalidHex_throws() {
        val json = """
            {
              "serviceUuid": "0000fff0-0000-1000-8000-00805f9b34fb",
              "commandCharacteristicUuid": "0000fff1-0000-1000-8000-00805f9b34fb",
              "commands": {
                "up": "0G",
                "down": "02",
                "stop": "00",
                "memory1": "11",
                "memory2": "12",
                "memory3": "13"
              }
            }
        """.trimIndent()

        val threw = try {
            DeskBleConfigParser.parse(json)
            false
        } catch (error: DeskBleConfigException) {
            true
        }

        assertTrue(threw)
    }
}
