package com.desk.moodboard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.desk.moodboard.data.model.NoteSyncStatus

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String,
    val language: String?,
    val createdAt: Long,
    val sessionId: String?,
    val syncStatus: NoteSyncStatus
)

