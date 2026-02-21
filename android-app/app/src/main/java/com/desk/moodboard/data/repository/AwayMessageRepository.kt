package com.desk.moodboard.data.repository

import com.desk.moodboard.data.local.AwayMessageDao
import com.desk.moodboard.data.local.AwayMessageEntity
import com.desk.moodboard.data.model.AwayMessageItem
import com.desk.moodboard.data.model.AwayTranscriptStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AwayMessageRepository(
    private val awayMessageDao: AwayMessageDao
) {
    fun observeMessages(): Flow<List<AwayMessageItem>> {
        return awayMessageDao.observeMessages().map { entities ->
            entities.map { it.toItem() }
        }
    }

    suspend fun upsertMessage(message: AwayMessageItem) {
        awayMessageDao.upsert(message.toEntity())
    }

    suspend fun updateTranscript(
        messageId: String,
        transcript: String?,
        status: AwayTranscriptStatus
    ) {
        val existing = awayMessageDao.getById(messageId) ?: return
        awayMessageDao.upsert(
            existing.copy(
                transcribedText = transcript,
                transcriptStatus = status.name
            )
        )
    }

    suspend fun deleteMessage(id: String) {
        awayMessageDao.deleteById(id)
    }

    private fun AwayMessageEntity.toItem() = AwayMessageItem(
        id = id,
        timestamp = timestamp,
        audioFilePath = audioFilePath,
        transcribedText = transcribedText,
        transcriptStatus = AwayTranscriptStatus.entries.firstOrNull { it.name == transcriptStatus }
            ?: AwayTranscriptStatus.FAILED
    )

    private fun AwayMessageItem.toEntity() = AwayMessageEntity(
        id = id,
        timestamp = timestamp,
        audioFilePath = audioFilePath,
        transcribedText = transcribedText,
        transcriptStatus = transcriptStatus.name
    )
}
