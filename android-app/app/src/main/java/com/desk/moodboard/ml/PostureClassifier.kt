package com.desk.moodboard.ml

import android.content.Context
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class PostureClassifier(context: Context) {

    private var interpreter: Interpreter? = null

    init {
        try {
            interpreter = Interpreter(loadModelFile(context, "posture_classifier.tflite"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun classify(result: PoseLandmarkerResult): PostureResult {
        if (interpreter == null || result.landmarks().isEmpty()) {
            return PostureResult(PostureState.UNKNOWN, 0f)
        }

        // Extract landmarks (simplified: take first person detected)
        val landmarks = result.landmarks()[0]
        
        // Prepare input tensor (33 landmarks * 3 coordinates = 99 floats)
        val input = FloatArray(33 * 3)
        landmarks.forEachIndexed { index, landmark ->
            input[index * 3] = landmark.x()
            input[index * 3 + 1] = landmark.y()
            input[index * 3 + 2] = landmark.z()
        }

        // Prepare output tensor (5 classes)
        val output = Array(1) { FloatArray(5) }

        // Run inference
        interpreter?.run(arrayOf(input), output)

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



