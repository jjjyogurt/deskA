package com.desk.moodboard.voice

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class VoiceMessageFileStore(
    private val context: Context
) {
    fun savePcmAsWav(
        samples: ShortArray,
        timestamp: Long = System.currentTimeMillis()
    ): String {
        val dir = File(context.filesDir, "away_messages")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Failed to create away message directory.")
        }

        val file = File(dir, "away_${timestamp}_${UUID.randomUUID()}.wav")
        val wavBytes = buildWavBytes(samples)
        FileOutputStream(file).use { output ->
            output.write(wavBytes)
            output.flush()
        }
        return file.absolutePath
    }

    fun exists(path: String): Boolean = File(path).exists()

    fun delete(path: String): Boolean {
        val file = File(path)
        return !file.exists() || file.delete()
    }

    private fun buildWavBytes(pcm: ShortArray): ByteArray {
        val pcmBytes = ByteBuffer.allocate(pcm.size * 2).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            for (sample in pcm) {
                putShort(sample)
            }
        }.array()

        val header = ByteArray(44)
        val totalDataLen = pcmBytes.size
        val totalLen = totalDataLen + 36

        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        writeIntLE(header, 4, totalLen)
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()

        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        writeIntLE(header, 16, 16)
        writeShortLE(header, 20, 1)
        writeShortLE(header, 22, 1)
        writeIntLE(header, 24, 16000)
        writeIntLE(header, 28, 16000 * 2)
        writeShortLE(header, 32, 2)
        writeShortLE(header, 34, 16)

        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        writeIntLE(header, 40, totalDataLen)

        return header + pcmBytes
    }

    private fun writeIntLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeShortLE(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
