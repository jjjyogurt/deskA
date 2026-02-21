package com.desk.moodboard.data.model

data class AwayMessageItem(
    val id: String,
    val timestamp: Long,
    val audioFilePath: String,
    val transcribedText: String?,
    val transcriptStatus: AwayTranscriptStatus
)
