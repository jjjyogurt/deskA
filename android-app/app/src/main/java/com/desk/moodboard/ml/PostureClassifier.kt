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
        
        // --- LANDMARK NORMALIZATION (Centering) ---
        // To make classification robust to user position, we center landmarks 
        // relative to the midpoint between shoulders (indices 11 and 12).
        if (landmarks.size < 33) {
            Log.w("PostureClassifier", "Insufficient landmarks: ${landmarks.size}")
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        
        val centerX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val centerY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val centerZ = (leftShoulder.z() + rightShoulder.z()) / 2f

        // Prepare input tensor (33 landmarks * 4 values = 132 floats)
        // Values are: centeredX, centeredY, centeredZ, visibility
        val input = FloatArray(33 * 4)
        landmarks.forEachIndexed { index, landmark ->
            val baseIdx = index * 4
            // Normalizing: subtract the shoulder center point
            input[baseIdx] = landmark.x() - centerX
            input[baseIdx + 1] = landmark.y() - centerY
            input[baseIdx + 2] = landmark.z() - centerZ
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
            0 -> PostureState.SITTING_STRAIGHT
            1 -> PostureState.SLOUCHING
            2 -> PostureState.LEANING_LEFT
            3 -> PostureState.LEANING_RIGHT
            4 -> PostureState.RECLINING
            else -> PostureState.UNKNOWN
        }

        return PostureResult(state, maxProb)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}











