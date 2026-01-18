package com.desk.moodboard.di

import com.desk.moodboard.data.remote.DoubaoService
import com.desk.moodboard.data.repository.CalendarRepository
import com.desk.moodboard.domain.ConflictDetector
import com.desk.moodboard.security.SecureKeyManager
import com.desk.moodboard.ui.assistant.AssistantViewModel
import com.desk.moodboard.ui.home.CalendarViewModel
import com.desk.moodboard.voice.AudioRecorder
import com.desk.moodboard.voice.VolcengineASRService
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { SecureKeyManager(androidContext()) }
    single { 
        val apiKey = "Zv7AE4-YmY15rJwWZXGfJodbv8d4Y2sj"
        val endpointId = "ep-20260111114926-fm8t8"
        DoubaoService(apiKey, endpointId)
    }
    single { CalendarRepository(androidContext()) }
    single { AudioRecorder(androidContext()) }
    single { 
        val appid = "5448745405"
        val token = "Zv7AE4-YmY15rJwWZXGfJodbv8d4Y2sj"
        val resourceId = "volc.bigasr.sauc.duration"
        VolcengineASRService(appid, token, resourceId)
    }
    single { ConflictDetector() }
    single { CalendarViewModel(get()) }

    viewModel { AssistantViewModel(getOrNull(), get(), get(), get(), get(), get()) }
}

