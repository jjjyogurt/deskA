package com.desk.moodboard.data.model

import kotlinx.serialization.Serializable
import com.desk.moodboard.data.model.CalendarEvent

@Serializable
data class ConflictInfo(
    val hasConflict: Boolean,
    val conflictingEvents: List<CalendarEvent>,
    val reasoning: String,
    val suggestedTimes: List<String> = emptyList()
)





