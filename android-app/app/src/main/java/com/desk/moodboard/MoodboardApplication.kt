package com.desk.moodboard

import android.app.Application
import com.desk.moodboard.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MoodboardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@MoodboardApplication)
            modules(appModule)
        }
    }
}

