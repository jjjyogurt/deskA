package com.desk.moodboard.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class AssistantIntentType { TODO, EVENT, CHAT }

@Serializable
data class AssistantIntent(
    val intentType: AssistantIntentType,
    val todo: TodoRequest? = null,
    val event: EventRequest? = null,
    val chatResponse: String? = null,
    val needsClarification: Boolean = false,
    val clarificationQuestion: String? = null,
    val confidence: Float? = null
)


