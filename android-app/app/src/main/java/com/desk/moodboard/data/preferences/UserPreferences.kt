package com.desk.moodboard.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {
    private val eInkModeKey = booleanPreferencesKey("e_ink_mode")

    val eInkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[eInkModeKey] ?: false
    }

    suspend fun setEInkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[eInkModeKey] = enabled
        }
    }
}
