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
        val landmarksList = result.landmarks()
        
        if (interpreter == null) {
            Log.e("PostureClassifier", "Inference failed: Interpreter is null.")
            return PostureResult(PostureState.UNKNOWN, 0f)
        }
        
        if (landmarksList.isEmpty()) {
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        // Extract landmarks (take first person detected)
        val landmarks = landmarksList[0]
        
        // --- HALLUCINATION GUARD ---
        // True human detections usually have higher visibility.
        // We sum up the visibility scores to ensure it's not a background "ghost".
        var totalVisibility = 0f
        landmarks.forEach { totalVisibility += it.visibility().orElse(0f) }
        val avgVisibility = totalVisibility / 33f
        if (avgVisibility < 0.5f) {
            Log.d("PostureClassifier", "Ignoring potential hallucination (Avg visibility: $avgVisibility)")
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        // --- LANDMARK NORMALIZATION (Centering & Scaling) ---
        if (landmarks.size < 33) {
            Log.w("PostureClassifier", "Insufficient landmarks: ${landmarks.size}")
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        
        // 1. Translation: Center relative to shoulders
        val centerX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val centerY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val centerZ = (leftShoulder.z() + rightShoulder.z()) / 2f

        // 2. Scale: Divide by shoulder distance to be distance-invariant
        val dx = leftShoulder.x() - rightShoulder.x()
        val dy = leftShoulder.y() - rightShoulder.y()
        val shoulderDistance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(0.01f)

        // Prepare input tensor (33 landmarks * 4 values = 132 floats)
        // Values are: (x-centerX)/scale, (y-centerY)/scale, (z-centerZ)/scale, visibility
        val input = FloatArray(33 * 4)
        landmarks.forEachIndexed { index, landmark ->
            val baseIdx = index * 4
            input[baseIdx] = (landmark.x() - centerX) / shoulderDistance
            input[baseIdx + 1] = (landmark.y() - centerY) / shoulderDistance
            input[baseIdx + 2] = (landmark.z() - centerZ) / shoulderDistance
            input[baseIdx + 3] = landmark.visibility().orElse(0f)
        }

        // Prepare output tensor (5 classes)
        val output = Array(1) { FloatArray(5) }

        try {
            // Run inference
            interpreter?.run(arrayOf(input), output)
        } catch (e: Exception) {
            Log.e("PostureClassifier", "Inference runtime error: ${e.message}", e)
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        // Find max probability
        val probabilities = output[0]
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

        // --- CONFIDENCE GUARD ---
        // If the model is not confident enough (threshold 0.35), return UNKNOWN.
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











