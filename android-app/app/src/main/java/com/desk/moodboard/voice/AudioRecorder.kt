package com.desk.moodboard.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.min

class AudioRecorder(private val context: Context) {
    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val REQUIRED_SAMPLES = 480000 // 30 seconds at 16kHz for Whisper
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _audioChunks = MutableSharedFlow<ShortArray>(extraBufferCapacity = 64)
    val audioChunks: SharedFlow<ShortArray> = _audioChunks

    private val audioData = mutableListOf<Short>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("MissingPermission")
    fun initialize(): Boolean {
        if (audioRecord != null) return true

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission denied")
            return false
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 4
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Initialization failed")
                release()
                return false
            }
            Log.d(TAG, "Initialized successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception during init", e)
            return false
        }
    }

    fun startRecording() {
        if (_isRecording.value) return
        val ar = audioRecord ?: if (initialize()) audioRecord else return
        
        audioData.clear()
        _isRecording.value = true
        
        try {
            ar!!.startRecording()
            recordingJob = scope.launch {
                val buffer = ShortArray(1600) // 100ms chunks at 16kHz
                var lastLogTime = 0L
                while (isActive && _isRecording.value) {
                    val read = ar.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val chunk = buffer.copyOf(read)
                        _audioChunks.tryEmit(chunk)
                        
                        var sum = 0.0
                        synchronized(audioData) {
                            for (i in 0 until read) {
                                sum += buffer[i].toDouble() * buffer[i].toDouble()
                                if (audioData.size < REQUIRED_SAMPLES) {
                                    audioData.add(buffer[i])
                                }
                            }
                        }
                        
                        // Log volume every 500ms to avoid flooding
                        val now = System.currentTimeMillis()
                        if (now - lastLogTime > 500) {
                            val rms = Math.sqrt(sum / read)
                            Log.d(TAG, "Recording... Current Volume (RMS): $rms")
                            lastLogTime = now
                        }
                    }
                    // Removed delay(10) to prevent sample loss. 
                    // ar.read is blocking, so this loop will wait naturally for audio.
                }
            }
            Log.d(TAG, "Started recording")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start", e)
            _isRecording.value = false
        }
    }

    fun stopRecording(): FloatArray {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null
        
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
        }

        val result = FloatArray(REQUIRED_SAMPLES)
        synchronized(audioData) {
            val collectedCount = audioData.size
            var maxVal = 0f
            
            // Standard Whisper expects audio normalized to [-1, 1]
            // We also calculate max amplitude to see if we're actually hearing anything
            for (i in 0 until min(collectedCount, REQUIRED_SAMPLES)) {
                val sample = audioData[i].toFloat() / 32768.0f
                result[i] = sample
                if (Math.abs(sample) > maxVal) maxVal = Math.abs(sample)
            }
            
            Log.d(TAG, "Stopped. Collected $collectedCount samples. Max Amplitude: $maxVal")
            
            // If the audio is extremely quiet (maxVal < 0.01), Whisper often outputs "use"
            if (maxVal < 0.01f && collectedCount > 0) {
                Log.w(TAG, "Warning: Audio is very quiet. This may cause 'use' hallucinations.")
            }
        }
        return result
    }

    fun stopRecordingRawPcm(): ShortArray {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping", e)
        }

        val result = ShortArray(audioData.size)
        synchronized(audioData) {
            for (i in audioData.indices) {
                result[i] = audioData[i]
            }
        }
        return result
    }

    fun release() {
        stopRecording()
        audioRecord?.release()
        audioRecord = null
        Log.d(TAG, "Released")
    }
}
