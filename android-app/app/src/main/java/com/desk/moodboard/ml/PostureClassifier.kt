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
        if (avgVisibility < 0.4f) {
            Log.d("PostureClassifier", "Ignoring potential hallucination (Avg visibility: $avgVisibility)")
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        // --- LANDMARK NORMALIZATION (Centering & Scaling) ---
        if (landmarks.size < 33) {
            Log.w("PostureClassifier", "Insufficient landmarks: ${landmarks.size}")
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        val nose = landmarks[0]
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        
        // 1. Translation: Center relative to shoulders
        val centerX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val centerY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val centerZ = (leftShoulder.z() + rightShoulder.z()) / 2f

        // 2. Stable Scale: Use vertical distance (Nose to Shoulder Line)
        // This is more stable for desk-work posture than shoulder width.
        val verticalScale = Math.abs(nose.y() - centerY).coerceAtLeast(0.05f)

        // Prepare input tensor (17 landmarks * 4 values = 68 floats)
        // Order: 8, 6, 4, 0, 1, 3, 7, 10, 9, 20, 16, 14, 12, 11, 13, 15, 19
        val targetIndices = listOf(8, 6, 4, 0, 1, 3, 7, 10, 9, 20, 16, 14, 12, 11, 13, 15, 19)
        val input = FloatArray(targetIndices.size * 4)
        
        targetIndices.forEachIndexed { index, landmarkIdx ->
            val landmark = landmarks[landmarkIdx]
            val baseIdx = index * 4
            // Mirror X coordinates by using (centerX - x) instead of (x - centerX)
            // This aligns un-mirrored MediaPipe input with mirrored training data
            input[baseIdx] = (centerX - landmark.x()) / verticalScale
            input[baseIdx + 1] = (landmark.y() - centerY) / verticalScale
            input[baseIdx + 2] = (landmark.z() - centerZ) / verticalScale
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











