package com.desk.moodboard

import android.app.Application
import com.desk.moodboard.di.appModule
import com.google.firebase.FirebaseApp
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class MoodboardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            android.util.Log.e("MoodboardApp", "Failed to initialize Firebase", e)
        }
        
        startKoin {
            androidLogger()
            androidContext(this@MoodboardApplication)
            modules(appModule)
        }
    }
}

