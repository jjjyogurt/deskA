package com.desk.moodboard.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureKeyManager(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun storeApiKey(apiKey: String) {
        encryptedPrefs.edit().putString("GEMINI_API_KEY", apiKey).apply()
    }

    fun getApiKey(): String? {
        return encryptedPrefs.getString("GEMINI_API_KEY", null)
    }
}






