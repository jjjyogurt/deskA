package com.desk.moodboard.voice

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperProcessor(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val modelPath = "whisper_base.tflite"

    companion object {
        private const val TAG = "WhisperProcessor"
        private const val EXPECTED_SAMPLES = 240000 // 15 seconds at 16kHz
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        Log.d(TAG, "INIT: Loading model $modelPath")
        try {
            val options = Interpreter.Options().apply {
                setNumThreads(Runtime.getRuntime().availableProcessors())
            }
            val modelBuffer = loadModelFile()
            interpreter = Interpreter(modelBuffer, options)
            Log.d(TAG, "INIT SUCCESS: Whisper ready")
        } catch (e: Exception) {
            Log.e(TAG, "INIT ERROR", e)
        }
    }

    private fun loadModelFile(): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    suspend fun transcribe(audioData: FloatArray): String = withContext(Dispatchers.Default) {
        val intr = interpreter ?: return@withContext "Error: Not initialized"

        try {
            // Ensure input is exactly EXPECTED_SAMPLES (240,000)
            val inputBuffer = ByteBuffer.allocateDirect(EXPECTED_SAMPLES * 4).apply {
                order(ByteOrder.nativeOrder())
                for (i in 0 until EXPECTED_SAMPLES) {
                    if (i < audioData.size) putFloat(audioData[i]) else putFloat(0f)
                }
                rewind()
            }

            // Prepare output buffer - typical Whisper TFLite models output tokens [1, 200]
            val outputTensor = intr.getOutputTensor(0)
            val outputShape = outputTensor.shape()
            val outputSize = outputShape.reduce { acc, i -> acc * i }
            
            val outputBuffer = ByteBuffer.allocateDirect(outputSize * 4).apply {
                order(ByteOrder.nativeOrder())
            }

            Log.d(TAG, "INFERENCE START")
            val startTime = System.currentTimeMillis()
            intr.run(inputBuffer, outputBuffer)
            val duration = System.currentTimeMillis() - startTime
            Log.d(TAG, "INFERENCE END: ${duration}ms")

            return@withContext decodeOutput(outputBuffer, outputSize)
        } catch (e: Exception) {
            Log.e(TAG, "TRANSCRIBE ERROR", e)
            return@withContext "Error: ${e.message}"
        }
    }

    private fun decodeOutput(buffer: ByteBuffer, size: Int): String {
        buffer.rewind()
        val tokens = IntArray(size)
        for (i in 0 until size) {
            tokens[i] = buffer.int
        }
        
        // In a real app, you'd use a vocabulary file to map these tokens to strings.
        // Since we don't have the vocabulary file yet, we'll return a meaningful message
        // and log the tokens for verification.
        Log.d(TAG, "Raw Tokens: ${tokens.joinToString(",")}")
        
        // Heuristic: If we got non-zero tokens, something was detected
        val detected = tokens.any { it > 0 && it != 50257 } // 50257 is often EOT
        return if (detected) {
            "Voice command detected. (Local Whisper)"
        } else {
            "No speech detected."
        }
    }

    fun release() {
        interpreter?.close()
        interpreter = null
    }
}
