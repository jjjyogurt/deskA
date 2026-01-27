package com.desk.moodboard.data.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

data class TodoItem(
    val id: String,
    val title: String,
    val description: String,
    val priority: Priority,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val createdAt: Long,
    val isDone: Boolean
)




