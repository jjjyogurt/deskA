package com.desk.moodboard.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.desk.moodboard.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {
    private val eInkModeKey = booleanPreferencesKey("e_ink_mode")
    private val appLanguageKey = stringPreferencesKey("app_language")

    val eInkMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[eInkModeKey] ?: false
    }

    val appLanguage: Flow<AppLanguage> = context.dataStore.data.map { prefs ->
        AppLanguage.fromId(prefs[appLanguageKey])
    }

    suspend fun setEInkMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[eInkModeKey] = enabled
        }
    }

    suspend fun setAppLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[appLanguageKey] = language.id
        }
    }
}
