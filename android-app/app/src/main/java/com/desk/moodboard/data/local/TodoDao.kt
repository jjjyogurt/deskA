package com.desk.moodboard.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query(
        """
        SELECT * FROM todos
        ORDER BY isDone ASC,
                 CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END,
                 dueDate ASC,
                 CASE WHEN dueTime IS NULL THEN 1 ELSE 0 END,
                 dueTime ASC,
                 createdAt DESC
        """
    )
    fun observeTodos(): Flow<List<TodoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(todo: TodoEntity)

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)
}





