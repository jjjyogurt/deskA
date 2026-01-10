package com.desk.moodboard.ml

import android.content.Context
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import org.tensorflow.lite.Interpreter
import org.json.JSONObject
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.util.Log

class PostureClassifier(context: Context) {

    private var interpreter: Interpreter? = null
    private var lastClsLog = 0L
    private val clsLogIntervalMs = 2000L
    
    // --- MATCH TRAINING SCRIPT CONFIG ---
    // TARGET_INDICES = [8, 6, 4, 0, 1, 3, 7, 10, 9, 20, 16, 14, 12, 11, 13, 15, 19]
    private val targetIndices = intArrayOf(8, 6, 4, 0, 1, 3, 7, 10, 9, 20, 16, 14, 12, 11, 13, 15, 19)
    private val numFeatures = targetIndices.size * 4 // 68
    
    // Normalization stats from training
    private var mean: FloatArray = FloatArray(numFeatures) { 0f }
    private var std: FloatArray = FloatArray(numFeatures) { 1f }

    init {
        Log.d("PostureClassifier", "Initializing PostureClassifier for 17 landmarks...")
        try {
            val modelFile = loadModelFile(context, "posture_classifier.tflite")
            
            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            
            interpreter = Interpreter(modelFile, options)
            
            val inputTensor = interpreter?.getInputTensor(0)
            Log.d("PostureClassifier", "Interpreter initialized. Input shape=${inputTensor?.shape()?.contentToString()}")
            
            loadNormStats(context)
        } catch (e: Exception) {
            Log.e("PostureClassifier", "CRITICAL: Failed to init interpreter: ${e.message}", e)
            interpreter = null
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
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

    private fun loadNormStats(context: Context) {
        try {
            val json = context.assets.open("norm_stats.json").bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val meanArr = obj.getJSONArray("mean")
            val stdArr = obj.getJSONArray("std")
            
            if (meanArr.length() != numFeatures || stdArr.length() != numFeatures) {
                Log.e("PostureClassifier", "Norm stats size mismatch: expected $numFeatures, got ${meanArr.length()}")
                return
            }

            for (i in 0 until numFeatures) {
                mean[i] = meanArr.getDouble(i).toFloat()
                std[i] = stdArr.getDouble(i).toFloat().let { if (it == 0f) 1e-6f else it }
            }
            Log.d("PostureClassifier", "Successfully loaded norm_stats.json")
        } catch (e: Exception) {
            Log.w("PostureClassifier", "Failed to load norm_stats.json; using defaults", e)
        }
    }

    fun classify(result: PoseLandmarkerResult): PostureResult {
        val landmarksList = result.landmarks()
        if (landmarksList.isEmpty()) return PostureResult(PostureState.UNKNOWN, 0f)
        
        val landmarks = landmarksList[0]
        if (landmarks.size < 21) { // Index 20 is our max in targetIndices
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        val intr = interpreter ?: return PostureResult(PostureState.UNKNOWN, 0f)

        // 1. RE-CENTERING AND SCALING (Match Python get_raw_features)
        // shoulder midpoint (Landmarks 11 and 12)
        val centerX = (landmarks[11].x() + landmarks[12].x()) / 2f
        val centerY = (landmarks[11].y() + landmarks[12].y()) / 2f
        val centerZ = (landmarks[11].z() + landmarks[12].z()) / 2f

        // vertical scale factor (Nose is landmark 0)
        val scale = Math.max(Math.abs(landmarks[0].y() - centerY), 0.05f)

        val frame = FloatArray(numFeatures)
        targetIndices.forEachIndexed { i, idx ->
            val lm = landmarks[idx]
            val base = i * 4
            // Landmarks are already mirrored because the input image to MediaPipe was flipped.
            frame[base] = (lm.x() - centerX) / scale
            frame[base + 1] = (lm.y() - centerY) / scale
            frame[base + 2] = (lm.z() - centerZ) / scale
            frame[base + 3] = lm.visibility().orElse(0f)
        }

        // 2. APPLY NORMALIZATION
        for (i in frame.indices) {
            frame[i] = (frame[i] - mean[i]) / std[i]
        }

        // 3. RUN INFERENCE
        val output = Array(1) { FloatArray(5) }
        try {
            intr.run(arrayOf(frame), output)
        } catch (e: Exception) {
            Log.e("PostureClassifier", "Inference runtime error: ${e.message}", e)
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        val probabilities = output[0]
        var maxIdx = 0
        var maxProb = 0f
        probabilities.forEachIndexed { index, prob ->
            if (prob > maxProb) {
                maxProb = prob
                maxIdx = index
            }
        }

        // Log occasionally
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastClsLog >= clsLogIntervalMs) {
            lastClsLog = now
            Log.d("PostureClassifier", "Probs=${probabilities.joinToString()} maxIdx=$maxIdx maxProb=$maxProb")
        }

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
