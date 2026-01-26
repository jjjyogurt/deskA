package com.desk.moodboard.data.model

data class NoteItem(
    val id: String,
    val title: String,
    val content: String,
    val language: String?,
    val createdAt: Long,
    val sessionId: String?,
    val syncStatus: NoteSyncStatus
)

