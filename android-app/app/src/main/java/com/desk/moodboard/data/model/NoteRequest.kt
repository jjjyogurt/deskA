package com.desk.moodboard.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NoteRequest(
    val title: String? = null,
    val content: String? = null,
    val language: String? = null,
    val confidence: Float? = null
)



