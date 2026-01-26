package com.desk.moodboard.data.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

@Serializable
data class TodoRequest(
    val title: String,
    val description: String = "",
    val priority: Priority = Priority.MEDIUM,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val createCalendarEvent: Boolean = false,
    val confidence: Float = 0f
)






