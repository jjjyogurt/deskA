package com.desk.moodboard.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AwayMessageDao {
    @Query("SELECT * FROM away_messages ORDER BY timestamp DESC")
    fun observeMessages(): Flow<List<AwayMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: AwayMessageEntity)

    @Query("SELECT * FROM away_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AwayMessageEntity?

    @Query("DELETE FROM away_messages WHERE id = :id")
    suspend fun deleteById(id: String)
}
