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
        val worldLandmarksList = result.worldLandmarks()
        
        if (interpreter == null) {
            Log.e("PostureClassifier", "Inference failed: Interpreter is null.")
            return PostureResult(PostureState.UNKNOWN, 0f)
        }
        
        if (worldLandmarksList.isEmpty()) {
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        // --- GEOMETRIC RATIO VECTOR ---
        // Industry Best Practice: Instead of coordinates, we use ratios of distances.
        // This is 100% invariant to translation, scale, and camera position.
        val landmarks = worldLandmarksList[0]
        
        // --- HALLUCINATION GUARD ---
        var totalVisibility = 0f
        landmarks.forEach { totalVisibility += it.visibility().orElse(0f) }
        val avgVisibility = totalVisibility / 33f
        if (avgVisibility < 0.4f) {
            Log.d("PostureClassifier", "Ignoring potential hallucination (Avg visibility: $avgVisibility)")
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        if (landmarks.size < 33) return PostureResult(PostureState.UNKNOWN, 0f)

        // 1. Define Key Points (World Landmarks in meters)
        val nose = landmarks[0]
        val lShoulder = landmarks[11]
        val rShoulder = landmarks[12]
        val lEye = landmarks[3]
        val rEye = landmarks[6]
        val lEar = landmarks[7]
        val rEar = landmarks[8]
        val lMouth = landmarks[9]
        val rMouth = landmarks[10]

        // 2. Reference Metric: 2D Shoulder Width (X and Y only)
        // 2D distance is MUCH more stable than 3D for scaling in desk apps.
        val dx = lShoulder.x() - rShoulder.x()
        val dy = lShoulder.y() - rShoulder.y()
        val shoulderWidth = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat().coerceAtLeast(0.1f)

        // 3. Calculate Mid-Points
        val midShoulderX = (lShoulder.x() + rShoulder.x()) / 2f
        val midShoulderY = (lShoulder.y() + rShoulder.y()) / 2f
        val midShoulderZ = (lShoulder.z() + rShoulder.z()) / 2f

        // 4. Generate 10 Geometric Ratios
        val input = FloatArray(10)
        
        // Ratio 1: Nose Height (Slouching - more positive = lower head)
        input[0] = (nose.y() - midShoulderY) / shoulderWidth
        
        // Ratio 2: Shoulder Tilt (Leaning - positive = left down)
        input[1] = (lShoulder.y() - rShoulder.y()) / shoulderWidth
        
        // Ratio 3: Nose Centering (Leaning/Rotation - offset from mid-shoulder)
        // Standard Orientation: (Nose.x - Mid.x). No manual mirror flip.
        input[2] = (nose.x() - midShoulderX) / shoulderWidth
        
        // Ratio 4: Nose Depth (Reclining - distance from shoulder plane)
        input[3] = (midShoulderZ - nose.z()) / shoulderWidth
        
        // Ratio 5 & 6: Eye Height relative to shoulders
        input[4] = (lEye.y() - midShoulderY) / shoulderWidth
        input[5] = (rEye.y() - midShoulderY) / shoulderWidth
        
        // Ratio 7 & 8: Ear Height relative to shoulders (Head tilt)
        input[6] = (lEar.y() - midShoulderY) / shoulderWidth
        input[7] = (rEar.y() - midShoulderY) / shoulderWidth
        
        // Ratio 9: Eye-to-Eye width (Zoom/Distance indicator)
        val edx = lEye.x() - rEye.x()
        val edy = lEye.y() - rEye.y()
        input[8] = Math.sqrt((edx * edx + edy * edy).toDouble()).toFloat() / shoulderWidth
        
        // Ratio 10: Mouth Height (Verticality)
        val midMouthY = (lMouth.y() + rMouth.y()) / 2f
        input[9] = (midMouthY - midShoulderY) / shoulderWidth

        // --- DEBUG LOGGING FOR PYTHON COMPARISON ---
        val inputString = input.joinToString(separator = ", ", prefix = "[", postfix = "]") { 
            String.format("%.4f", it) 
        }
        Log.d("PostureClassifier", "GEOM_RATIO_DATA: $inputString")

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











