package com.desk.moodboard.data.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDateTime

@Serializable
data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String = "",
    val startTime: LocalDateTime,
    val endTime: LocalDateTime? = null,
    val location: String = "",
    val attendees: List<String> = emptyList(),
    val eventType: EventType = EventType.MEETING
)








