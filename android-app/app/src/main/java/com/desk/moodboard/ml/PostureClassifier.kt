package com.desk.moodboard.ml

import android.content.Context
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import org.tensorflow.lite.Interpreter
import java.util.Optional
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.util.Log

class PostureClassifier(context: Context) {

    private var interpreter: Interpreter? = null
    
    init {
        Log.d("PostureClassifier", "Initializing PostureClassifier init block...")
        try {
            val modelFile = loadModelFile(context, "posture_classifier.tflite")
            
            // Explicitly set options for better compatibility/performance
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            
            interpreter = Interpreter(modelFile, options)
            
            val inputTensor = interpreter?.getInputTensor(0)
            val outputTensor = interpreter?.getOutputTensor(0)
            
            Log.d("PostureClassifier", "Interpreter initialized successfully.")
            Log.d("PostureClassifier", "Input tensor: shape=${inputTensor?.shape()?.contentToString()}, type=${inputTensor?.dataType()}")
            Log.d("PostureClassifier", "Output tensor: shape=${outputTensor?.shape()?.contentToString()}, type=${outputTensor?.dataType()}")
        } catch (e: Exception) {
            Log.e("PostureClassifier", "CRITICAL: Failed to init interpreter: ${e.message}", e)
            interpreter = null
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        Log.d("PostureClassifier", "Loading model file from assets: $modelPath")
        return try {
            val fileDescriptor = context.assets.openFd(modelPath)
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        } catch (e: Exception) {
            Log.e("PostureClassifier", "Failed to map model file $modelPath: ${e.message}")
            throw e
        }
    }

    fun classify(result: PoseLandmarkerResult): PostureResult {
        // Use normalized image landmarks (camera space)
        val landmarks = result.landmarks().firstOrNull()
        if (landmarks == null || landmarks.size < 33) {
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        val intr = interpreter
        if (intr == null) {
            Log.e("PostureClassifier", "Inference failed: Interpreter is null.")
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        // Build one frame of 132 features (x, y, z, visibility for 33 landmarks)
        val frame = FloatArray(132)
        for (i in 0 until 33) {
            val lm = landmarks[i]
            val base = i * 4
            frame[base] = lm.x()
            frame[base + 1] = lm.y()
            frame[base + 2] = lm.z()
            frame[base + 3] = lm.visibility().orElse(0f)
        }

        // Prepare output buffer using model's output shape
        val outShape = intr.getOutputTensor(0).shape()
        val outBatch = if (outShape.isNotEmpty()) outShape[0] else 1
        val outClasses = if (outShape.size >= 2) outShape[1] else 5
        val output = Array(outBatch) { FloatArray(outClasses) }

        try {
            intr.run(arrayOf(frame), output)
        } catch (e: Exception) {
            Log.e("PostureClassifier", "Inference runtime error: ${e.message}", e)
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        val probabilities = output.firstOrNull() ?: return PostureResult(PostureState.UNKNOWN, 0f)
        var maxIdx = 0
        var maxProb = 0f
        probabilities.forEachIndexed { index, prob ->
            if (prob > maxProb) {
                maxProb = prob
                maxIdx = index
            }
        }

        Log.d(
            "PostureClassifier",
            "Raw probs=${probabilities.joinToString()} maxIdx=$maxIdx maxProb=$maxProb"
        )

        if (maxProb < 0.35f) {
            return PostureResult(PostureState.UNKNOWN, maxProb)
        }

        val state = when (maxIdx) {
            0 -> PostureState.LEANING_LEFT
            1 -> PostureState.LEANING_RIGHT
            2 -> PostureState.RECLINING
            3 -> PostureState.SITTING_STRAIGHT
            4 -> PostureState.SLOUCHING
            else -> PostureState.UNKNOWN
        }

        return PostureResult(state, maxProb)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}











