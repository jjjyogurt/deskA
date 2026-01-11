package com.desk.moodboard.domain

import com.desk.moodboard.data.model.CalendarEvent
import com.desk.moodboard.data.model.ConflictInfo
import com.desk.moodboard.data.model.EventRequest
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone

class ConflictDetector {
    
    fun detectConflicts(
        eventRequest: EventRequest,
        existingEvents: List<CalendarEvent>
    ): ConflictInfo {
        val startTime = eventRequest.startTime ?: return noConflict()
        val endTime = eventRequest.endTime ?: startTime.toInstant(TimeZone.currentSystemDefault())
            .plus(eventRequest.duration?.toLong() ?: 60, DateTimeUnit.MINUTE)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        
        val eventDate = startTime.date
        
        // Filter same-day events
        val sameDayEvents = existingEvents.filter { 
            it.startTime.date == eventDate 
        }
        
        // Check overlaps
        val conflicts = sameDayEvents.filter { existing ->
            val existingEnd = existing.endTime ?: existing.startTime.toInstant(TimeZone.currentSystemDefault())
                .plus(1, DateTimeUnit.HOUR)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            
            startTime < existingEnd && endTime > existing.startTime
        }
        
        if (conflicts.isEmpty()) return noConflict()
        
        return ConflictInfo(
            hasConflict = true,
            conflictingEvents = conflicts,
            reasoning = "Time conflicts with: ${conflicts.joinToString { it.title }}"
        )
    }
    
    private fun noConflict() = ConflictInfo(
        hasConflict = false,
        conflictingEvents = emptyList(),
        reasoning = "No conflicts detected"
    )
}

