package com.desk.moodboard.ml

enum class PostureState(val label: String) {
    SITTING_STRAIGHT("Sitting Straight"),
    SLOUCHING("Slouching"),
    LEANING_LEFT("Leaning Left"),
    LEANING_RIGHT("Leaning Right"),
    RECLINING("Reclining"),
    UNKNOWN("Unknown")
}

data class PostureResult(
    val state: PostureState,
    val confidence: Float,
    val inferenceTime: Long = 0L
)



