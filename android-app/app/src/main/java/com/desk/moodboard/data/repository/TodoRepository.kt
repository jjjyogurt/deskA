package com.desk.moodboard.data.repository

import com.desk.moodboard.data.local.TodoDao
import com.desk.moodboard.data.local.TodoEntity
import com.desk.moodboard.data.model.TodoItem
import com.desk.moodboard.data.model.TodoRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class TodoRepository(private val todoDao: TodoDao) {
    fun observeTodos(): Flow<List<TodoItem>> {
        return todoDao.observeTodos().map { entities ->
            entities.map { it.toItem() }
        }
    }

    suspend fun insertFromRequest(request: TodoRequest): String {
        val id = UUID.randomUUID().toString()
        val entity = TodoEntity(
            id = id,
            title = request.title,
            description = request.description,
            priority = request.priority,
            dueDate = request.dueDate,
            dueTime = request.dueTime,
            createdAt = System.currentTimeMillis(),
            isDone = false
        )
        todoDao.upsert(entity)
        return id
    }

    suspend fun update(item: TodoItem) {
        todoDao.update(item.toEntity())
    }

    private fun TodoEntity.toItem() = TodoItem(
        id = id,
        title = title,
        description = description,
        priority = priority,
        dueDate = dueDate,
        dueTime = dueTime,
        createdAt = createdAt,
        isDone = isDone
    )

    private fun TodoItem.toEntity() = TodoEntity(
        id = id,
        title = title,
        description = description,
        priority = priority,
        dueDate = dueDate,
        dueTime = dueTime,
        createdAt = createdAt,
        isDone = isDone
    )
}

