package com.desk.moodboard.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [TodoEntity::class, NoteEntity::class, AwayMessageEntity::class], version = 3, exportSchema = false)
@TypeConverters(TodoConverters::class)
abstract class TodoDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao
    abstract fun noteDao(): NoteDao
    abstract fun awayMessageDao(): AwayMessageDao
}


