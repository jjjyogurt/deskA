package com.desk.moodboard.i18n

import androidx.core.os.LocaleListCompat

enum class AppLanguage(
    val id: String,
    val languageTag: String?,
) {
    SYSTEM(id = "system", languageTag = null),
    ENGLISH(id = "en", languageTag = "en"),
    CHINESE_SIMPLIFIED(id = "zh-CN", languageTag = "zh-CN");

    fun toLocaleListCompat(): LocaleListCompat {
        return if (languageTag == null) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
    }

    companion object {
        fun fromId(raw: String?): AppLanguage {
            return entries.firstOrNull { it.id == raw } ?: SYSTEM
        }
    }
}
