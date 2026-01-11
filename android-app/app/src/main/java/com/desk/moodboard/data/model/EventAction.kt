package com.desk.moodboard.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class EventAction { CREATE, LIST, UPDATE, DELETE, QUERY, CHAT }

