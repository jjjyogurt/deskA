package com.desk.moodboard.di

import androidx.room.Room
import com.desk.moodboard.data.local.TodoDatabase
import com.desk.moodboard.data.ble.DeskBleClient
import com.desk.moodboard.data.ble.DeskBleConfigLoader
import com.desk.moodboard.data.ble.DeskBleRepository
import com.desk.moodboard.data.remote.DoubaoService
import com.desk.moodboard.data.repository.CalendarRepository
import com.desk.moodboard.data.repository.NoteRepository
import com.desk.moodboard.data.repository.TodoRepository
import com.desk.moodboard.domain.ConflictDetector
import com.desk.moodboard.security.SecureKeyManager
import com.desk.moodboard.ui.assistant.AssistantViewModel
import com.desk.moodboard.ui.assistant.VoiceAgentViewModel
import com.desk.moodboard.ui.desk.DeskControlViewModel
import com.desk.moodboard.ui.home.CalendarViewModel
import com.desk.moodboard.ui.home.TodoViewModel
import com.desk.moodboard.voice.AudioRecorder
import com.desk.moodboard.voice.VolcengineASRService
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { SecureKeyManager(androidContext()) }
    single { 
        val apiKey = "c7dbff8b-3031-4009-92da-6b4db8b97b1b"
        val endpointId = "ep-20260111114926-fm8t8"
        DoubaoService(apiKey, endpointId)
    }
    single { CalendarRepository(androidContext()) }
    single {
        Room.databaseBuilder(androidContext(), TodoDatabase::class.java, "todo.db")
            .fallbackToDestructiveMigration()
            .build()
    }
    single { get<TodoDatabase>().todoDao() }
    single { get<TodoDatabase>().noteDao() }
    single { TodoRepository(get()) }
    single { NoteRepository(get()) }
    single { AudioRecorder(androidContext()) }
    single { 
        val appid = "5448745405"
        val token = "Zv7AE4-YmY15rJwWZXGfJodbv8d4Y2sj"
        val resourceId = "volc.bigasr.sauc.duration"
        VolcengineASRService(appid, token, resourceId)
    }
    single { DeskBleConfigLoader(androidContext(), "desk_ble_config.json") }
    single { DeskBleClient(androidContext()) }
    single { DeskBleRepository(get(), get()) }
    single { ConflictDetector() }
    single { CalendarViewModel(get()) }

    viewModel { VoiceAgentViewModel(getOrNull(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { AssistantViewModel(getOrNull(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { TodoViewModel(getOrNull(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { DeskControlViewModel(get()) }
}

