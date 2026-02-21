package com.desk.moodboard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "away_messages")
data class AwayMessageEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val audioFilePath: String,
    val transcribedText: String?,
    val transcriptStatus: String
)
