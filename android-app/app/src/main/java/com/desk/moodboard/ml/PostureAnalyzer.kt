package com.desk.moodboard.ml

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class PostureAnalyzer(
    private val poseLandmarkerHelper: PoseLandmarkerHelper
) : ImageAnalysis.Analyzer {

    private var lastInferenceTime = 0L
    private val inferenceInterval = 333L // 3fps = ~333ms

    override fun analyze(image: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        // Throttling to 3fps
        if (currentTime - lastInferenceTime >= inferenceInterval) {
            lastInferenceTime = currentTime
            
            // Front camera is typical for this app's use case
            poseLandmarkerHelper.detectLiveStream(image, true)
        }
        
        // Always close the image proxy to avoid blocking the pipeline
        image.close()
    }
}



