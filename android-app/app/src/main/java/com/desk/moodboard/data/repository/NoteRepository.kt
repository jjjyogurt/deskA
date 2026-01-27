package com.desk.moodboard.data.repository

import com.desk.moodboard.data.local.NoteDao
import com.desk.moodboard.data.local.NoteEntity
import com.desk.moodboard.data.model.NoteItem
import com.desk.moodboard.data.model.NoteRequest
import com.desk.moodboard.data.model.NoteSyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class NoteRepository(private val noteDao: NoteDao) {
    fun observeNotes(): Flow<List<NoteItem>> {
        return noteDao.observeNotes().map { entities ->
            entities.map { it.toItem() }
        }
    }

    suspend fun insertFromRequest(request: NoteRequest, sessionId: String?): String {
        val content = request.content?.trim().orEmpty()
        if (content.isBlank()) {
            throw IllegalArgumentException("Note content is required.")
        }
        val title = request.title?.trim().orEmpty().ifBlank { "Idea Note" }
        val id = UUID.randomUUID().toString()
        val entity = NoteEntity(
            id = id,
            title = title,
            content = content,
            language = request.language,
            createdAt = System.currentTimeMillis(),
            sessionId = sessionId,
            syncStatus = NoteSyncStatus.LOCAL_ONLY
        )
        noteDao.upsert(entity)
        return id
    }

    private fun NoteEntity.toItem() = NoteItem(
        id = id,
        title = title,
        content = content,
        language = language,
        createdAt = createdAt,
        sessionId = sessionId,
        syncStatus = syncStatus
    )
}



