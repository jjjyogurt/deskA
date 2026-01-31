package com.desk.moodboard.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.desk.moodboard.data.model.Priority
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val priority: Priority,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val createdAt: Long,
    val isDone: Boolean
)





