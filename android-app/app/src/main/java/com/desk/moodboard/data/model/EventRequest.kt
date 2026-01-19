package com.desk.moodboard.data.model

import kotlinx.serialization.Serializable
import kotlinx.datetime.LocalDateTime

@Serializable
data class EventRequest(
    val action: EventAction,
    val chatResponse: String? = null,
    val needsClarification: Boolean = false,
    val clarificationQuestion: String? = null,
    val title: String = "",
    val description: String = "",
    val startTime: LocalDateTime? = null,
    val endTime: LocalDateTime? = null,
    val duration: Int? = null,
    val location: String = "",
    val attendees: List<String> = emptyList(),
    val eventType: EventType = EventType.MEETING,
    val confidence: Float = 0f
)

