package com.desk.moodboard.data.local

import androidx.room.TypeConverter
import com.desk.moodboard.data.model.NoteSyncStatus
import com.desk.moodboard.data.model.Priority
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class TodoConverters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it) }

    @TypeConverter
    fun fromPriority(value: Priority): String = value.name

    @TypeConverter
    fun toPriority(value: String): Priority = Priority.valueOf(value)

    @TypeConverter
    fun fromNoteSyncStatus(value: NoteSyncStatus): String = value.name

    @TypeConverter
    fun toNoteSyncStatus(value: String): NoteSyncStatus = NoteSyncStatus.valueOf(value)
}


