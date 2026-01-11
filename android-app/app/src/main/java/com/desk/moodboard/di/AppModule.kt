package com.desk.moodboard.di

import com.desk.moodboard.data.remote.DoubaoService
import com.desk.moodboard.data.repository.CalendarRepository
import com.desk.moodboard.domain.ConflictDetector
import com.desk.moodboard.security.SecureKeyManager
import com.desk.moodboard.ui.assistant.AssistantViewModel
import com.desk.moodboard.ui.home.CalendarViewModel
import com.desk.moodboard.voice.VoiceProcessor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { SecureKeyManager(androidContext()) }
    single { 
        val apiKey = "56fa7dc0-d4b5-414c-b2a3-ce4a580f8e89"
        val endpointId = "ep-20260111114926-fm8t8"
        DoubaoService(apiKey, endpointId)
    }
    single { CalendarRepository(androidContext()) }
    single { VoiceProcessor(get<DoubaoService>()) }
    single { ConflictDetector() }
    single { CalendarViewModel(get()) }

    viewModel { AssistantViewModel(getOrNull(), get(), get(), get(), get<CalendarViewModel>()) }
}

