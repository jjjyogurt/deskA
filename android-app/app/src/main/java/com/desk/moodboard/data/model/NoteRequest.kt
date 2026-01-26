package com.desk.moodboard.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NoteRequest(
    val title: String,
    val content: String,
    val language: String? = null,
    val confidence: Float? = null
)

