package com.desk.moodboard.voice

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import com.desk.moodboard.data.remote.DoubaoService
import java.io.File
import java.io.IOException

class VoiceProcessor(
    private val doubaoService: DoubaoService
) {
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

    fun startRecording(context: Context) {
        if (isRecording) return

        try {
            audioFile = File(context.cacheDir, "voice_input.m4a")
            mediaRecorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
        } catch (e: Exception) {
            Log.e("VoiceProcessor", "startRecording failed", e)
            isRecording = false
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null

        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            audioFile
        } catch (e: Exception) {
            Log.e("VoiceProcessor", "stopRecording failed", e)
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            null
        }
    }

    suspend fun processAudio(audioFile: File): String? {
        val audioBytes = audioFile.readBytes()
        return doubaoService.transcribeAudio(audioBytes)
    }
}

