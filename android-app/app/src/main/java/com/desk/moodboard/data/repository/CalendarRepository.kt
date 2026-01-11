package com.desk.moodboard.data.repository

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import androidx.work.*
import com.desk.moodboard.data.model.CalendarEvent
import com.desk.moodboard.data.model.EventRequest
import com.desk.moodboard.service.EventReminderWorker
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import java.util.*
import java.util.concurrent.TimeUnit

class CalendarRepository(private val context: Context) {

    fun getEvents(startDate: LocalDateTime, endDate: LocalDateTime): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION
        )

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(
            startDate.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds().toString(),
            endDate.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds().toString()
        )

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(CalendarContract.Events._ID)
            val titleIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val descIndex = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
            val startIndex = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
            val endIndex = cursor.getColumnIndex(CalendarContract.Events.DTEND)
            val locIndex = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)

            while (cursor.moveToNext()) {
                val startMs = cursor.getLong(startIndex)
                val endMs = cursor.getLong(endIndex)
                
                events.add(CalendarEvent(
                    id = cursor.getString(idIndex),
                    title = cursor.getString(titleIndex) ?: "",
                    description = cursor.getString(descIndex) ?: "",
                    startTime = Instant.fromEpochMilliseconds(startMs).toLocalDateTime(TimeZone.currentSystemDefault()),
                    endTime = if (endMs > 0) Instant.fromEpochMilliseconds(endMs).toLocalDateTime(TimeZone.currentSystemDefault()) else null,
                    location = cursor.getString(locIndex) ?: ""
                ))
            }
        }
        return events
    }

    fun createEvent(request: EventRequest): Boolean {
        val startTime = request.startTime ?: return false
        val endTime = request.endTime ?: startTime.toInstant(TimeZone.currentSystemDefault())
            .plus(request.duration?.toLong() ?: 60L, DateTimeUnit.MINUTE)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds())
            put(CalendarContract.Events.DTEND, endTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds())
            put(CalendarContract.Events.TITLE, request.title)
            put(CalendarContract.Events.DESCRIPTION, request.description)
            put(CalendarContract.Events.EVENT_LOCATION, request.location)
            put(CalendarContract.Events.CALENDAR_ID, 1) // Assuming default calendar for now
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.currentSystemDefault().id)
        }

        val uri = try {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (e: Exception) {
            android.util.Log.e("CalendarRepository", "Failed to insert event", e)
            null
        }
        android.util.Log.d("CalendarRepository", "Event creation result: ${uri != null}")
        
        if (uri != null) {
            scheduleReminder(request.title, startTime)
        }
        
        return uri != null
    }

    private fun scheduleReminder(title: String, startTime: LocalDateTime) {
        val workManager = WorkManager.getInstance(context)
        
        val eventTimeMs = startTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        val currentTimeMs = System.currentTimeMillis()
        val delayMs = eventTimeMs - currentTimeMs - (15 * 60 * 1000) // 15 minutes before

        if (delayMs > 0) {
            val data = workDataOf(
                "event_title" to title,
                "event_time" to startTime.time.toString()
            )

            val reminderRequest = OneTimeWorkRequestBuilder<EventReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .build()

            workManager.enqueueUniqueWork(
                "reminder_$title",
                ExistingWorkPolicy.REPLACE,
                reminderRequest
            )
            android.util.Log.d("CalendarRepository", "Reminder scheduled for $title in ${delayMs / 1000}s")
        }
    }
}

