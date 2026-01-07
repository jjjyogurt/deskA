package com.desk.moodboard.ml

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Lightweight overlay data for drawing pose landmarks on the preview.
 */
data class PoseOverlay(
    val landmarks: List<NormalizedLandmark>,
    val imageWidth: Int,
    val imageHeight: Int,
    val isFrontCamera: Boolean = true
)

