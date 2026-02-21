package com.desk.moodboard.data.repository

import com.desk.moodboard.data.local.AwayMessageDao
import com.desk.moodboard.data.local.AwayMessageEntity
import com.desk.moodboard.data.model.AwayMessageItem
import com.desk.moodboard.data.model.AwayTranscriptStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AwayMessageRepositoryTest {

    @Test
    fun observeMessages_mapsUnknownStatusToFailed() = runBlocking {
        val dao = FakeAwayMessageDao(
            initial = listOf(
                AwayMessageEntity(
                    id = "1",
                    timestamp = 10L,
                    audioFilePath = "/tmp/one.wav",
                    transcribedText = "hello",
                    transcriptStatus = "UNKNOWN"
                )
            )
        )
        val repository = AwayMessageRepository(dao)

        val result = repository.observeMessages().first()

        assertEquals(1, result.size)
        assertEquals(AwayTranscriptStatus.FAILED, result.first().transcriptStatus)
    }

    @Test
    fun upsertMessage_persistsEntityWithStatus() = runBlocking {
        val dao = FakeAwayMessageDao()
        val repository = AwayMessageRepository(dao)

        repository.upsertMessage(
            AwayMessageItem(
                id = "message-1",
                timestamp = 100L,
                audioFilePath = "/tmp/message-1.wav",
                transcribedText = null,
                transcriptStatus = AwayTranscriptStatus.PENDING
            )
        )

        val stored = dao.getById("message-1")
        assertEquals("/tmp/message-1.wav", stored?.audioFilePath)
        assertEquals("PENDING", stored?.transcriptStatus)
    }

    @Test
    fun updateTranscript_updatesOnlyTranscriptFields() = runBlocking {
        val dao = FakeAwayMessageDao(
            initial = listOf(
                AwayMessageEntity(
                    id = "message-2",
                    timestamp = 200L,
                    audioFilePath = "/tmp/message-2.wav",
                    transcribedText = null,
                    transcriptStatus = "PENDING"
                )
            )
        )
        val repository = AwayMessageRepository(dao)

        repository.updateTranscript(
            messageId = "message-2",
            transcript = "new transcript",
            status = AwayTranscriptStatus.READY
        )

        val updated = dao.getById("message-2")
        assertEquals("/tmp/message-2.wav", updated?.audioFilePath)
        assertEquals("new transcript", updated?.transcribedText)
        assertEquals("READY", updated?.transcriptStatus)
    }

    @Test
    fun updateTranscript_ignoresMissingMessage() = runBlocking {
        val dao = FakeAwayMessageDao()
        val repository = AwayMessageRepository(dao)

        repository.updateTranscript(
            messageId = "missing",
            transcript = "text",
            status = AwayTranscriptStatus.READY
        )

        assertNull(dao.getById("missing"))
    }
}

private class FakeAwayMessageDao(
    initial: List<AwayMessageEntity> = emptyList()
) : AwayMessageDao {
    private val state = MutableStateFlow(initial)

    override fun observeMessages(): Flow<List<AwayMessageEntity>> = state

    override suspend fun upsert(message: AwayMessageEntity) {
        val current = state.value
        val existingIndex = current.indexOfFirst { it.id == message.id }
        state.value = if (existingIndex >= 0) {
            current.toMutableList().also { it[existingIndex] = message }
        } else {
            current + message
        }
    }

    override suspend fun getById(id: String): AwayMessageEntity? {
        return state.value.firstOrNull { it.id == id }
    }

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }
}
