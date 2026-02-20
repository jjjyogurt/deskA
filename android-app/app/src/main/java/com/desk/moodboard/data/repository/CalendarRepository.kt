package com.desk.moodboard.data.repository

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import com.desk.moodboard.data.model.CalendarEvent
import com.desk.moodboard.data.model.EventRequest
import com.desk.moodboard.service.EventReminderWorker
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import java.util.*
import java.util.concurrent.TimeUnit

sealed interface CalendarCreateResult {
    data class Success(val eventId: String?) : CalendarCreateResult
    data class PermissionDenied(val missingRead: Boolean, val missingWrite: Boolean) : CalendarCreateResult
    data object NoWritableCalendar : CalendarCreateResult
    data class InvalidInput(val reason: String) : CalendarCreateResult
    data class ProviderError(val reason: String?) : CalendarCreateResult
}

class CalendarRepository(private val context: Context) {

    fun getEvents(startDate: LocalDateTime, endDate: LocalDateTime): List<CalendarEvent> {
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            return emptyList()
        }
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

    fun createEvent(request: EventRequest): CalendarCreateResult {
        val hasReadCalendar = hasPermission(Manifest.permission.READ_CALENDAR)
        val hasWriteCalendar = hasPermission(Manifest.permission.WRITE_CALENDAR)
        if (!hasReadCalendar || !hasWriteCalendar) {
            return CalendarCreateResult.PermissionDenied(
                missingRead = !hasReadCalendar,
                missingWrite = !hasWriteCalendar
            )
        }

        val startTime = request.startTime
            ?: return CalendarCreateResult.InvalidInput("Missing event start time.")
        val durationMinutes = request.duration?.takeIf { it > 0 } ?: 60
        val endTime = request.endTime ?: startTime.toInstant(TimeZone.currentSystemDefault())
            .plus(durationMinutes.toLong(), DateTimeUnit.MINUTE)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        if (endTime <= startTime) {
            return CalendarCreateResult.InvalidInput("Event end time must be after start time.")
        }

        val calendarId = resolveWritableCalendarId() ?: return CalendarCreateResult.NoWritableCalendar

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, startTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds())
            put(CalendarContract.Events.DTEND, endTime.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds())
            put(CalendarContract.Events.TITLE, request.title)
            put(CalendarContract.Events.DESCRIPTION, request.description)
            put(CalendarContract.Events.EVENT_LOCATION, request.location)
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.currentSystemDefault().id)
        }

        val uri = try {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Failed to insert event", e)
            null
        }
        Log.d("CalendarRepository", "Event creation result: ${uri != null}, calendarId=$calendarId")

        return if (uri != null) {
            scheduleReminder(request.title, startTime)
            CalendarCreateResult.Success(uri.lastPathSegment)
        } else {
            CalendarCreateResult.ProviderError("Calendar provider insert returned null URI.")
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun resolveWritableCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        )

        var selectedCalendarId: Long? = null
        var selectedScore = Int.MIN_VALUE

        val cursor = try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
        } catch (error: Exception) {
            Log.e("CalendarRepository", "Failed to query calendars", error)
            return null
        }

        cursor?.use { rows ->
            val idIndex = rows.getColumnIndex(CalendarContract.Calendars._ID)
            val primaryIndex = rows.getColumnIndex(CalendarContract.Calendars.IS_PRIMARY)
            val visibleIndex = rows.getColumnIndex(CalendarContract.Calendars.VISIBLE)
            val syncEventsIndex = rows.getColumnIndex(CalendarContract.Calendars.SYNC_EVENTS)
            val accessIndex = rows.getColumnIndex(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL)

            while (rows.moveToNext()) {
                val accessLevel = rows.getInt(accessIndex)
                if (accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
                    continue
                }

                val calendarId = rows.getLong(idIndex)
                val isPrimary = rows.getInt(primaryIndex) == 1
                val isVisible = rows.getInt(visibleIndex) == 1
                val syncEnabled = rows.getInt(syncEventsIndex) == 1

                val score = accessLevel +
                    (if (isPrimary) 100 else 0) +
                    (if (isVisible) 20 else 0) +
                    (if (syncEnabled) 20 else 0)

                if (score > selectedScore) {
                    selectedScore = score
                    selectedCalendarId = calendarId
                }
            }
        }

        Log.d("CalendarRepository", "Selected writable calendarId=$selectedCalendarId")
        return selectedCalendarId
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
            Log.d("CalendarRepository", "Reminder scheduled for $title in ${delayMs / 1000}s")
        }
    }
}

