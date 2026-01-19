package com.desk.moodboard.data.remote

import android.util.Log
import com.desk.moodboard.data.model.EventRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import java.util.concurrent.TimeUnit

@Serializable
data class DoubaoChatRequest(
    val model: String,
    val messages: List<DoubaoMessage>,
    val temperature: Float = 0.3f,
    val caching: DoubaoCaching? = null
)

@Serializable
data class DoubaoMessage(
    val role: String,
    val content: String
)

@Serializable
data class DoubaoChatResponse(
    val id: String? = null,
    val choices: List<DoubaoChoice>
)

@Serializable
data class DoubaoChoice(
    val message: DoubaoMessage
)

@Serializable
data class DoubaoCaching(
    val type: String = "enabled",
    val prefix: Boolean = true
)

class DoubaoService(
    private val apiKey: String,
    private val endpointId: String = "ep-20260118231116-btzkj"
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
        
    private val json = Json { 
        ignoreUnknownKeys = true 
        coerceInputValues = true
        isLenient = true
    }
    
    private val baseUrl = "https://ark.cn-beijing.volces.com/api/v3/chat/completions"

    suspend fun parseNaturalLanguage(input: String): EventRequest? {
        val systemPrompt = """
            You are an AI calendar assistant. Parse natural language input into structured requests.
            
            Return ONLY JSON: {
                "action": "CREATE|LIST|UPDATE|DELETE|QUERY|CHAT",
                "chatResponse": "Your natural conversational response if the action is CHAT",
                "needsClarification": true,
                "clarificationQuestion": "If needed, ask a follow-up question",
                "title": "event title",
                "description": "additional details",
                "startTime": "YYYY-MM-DDTHH:MM:SS",
                "endTime": "YYYY-MM-DDTHH:MM:SS",
                "duration": 60,
                "location": "location if mentioned",
                "attendees": ["person1", "person2"],
                "eventType": "MEETING|APPOINTMENT|REMINDER|DEADLINE|TASK",
                "confidence": 0.8
            }
            
            Rules:
            1. Use action "CHAT" and fill "chatResponse" for general conversation or questions that don't require calendar changes.
            2. For calendar tasks, use the appropriate action (CREATE, LIST, etc.).
            3. Use ISO format for dates. Current year is 2026.
            4. IMPORTANT: If a field is not applicable, use null instead of an empty string. Especially for startTime and endTime.
            5. Default time: 09:00 if not specified. Default duration: 60 minutes.
            6. If the request is ambiguous (e.g., "set a Friday meeting"), set needsClarification=true and ask a specific question in clarificationQuestion (e.g., "Which Friday?").
            7. If the user says "meeting with <name>", set the title to "Meeting with <name>".
        """.trimIndent()

        val currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
        val userPrompt = "Current date/time: $currentTime\nParse this input: \"$input\""

        val requestBody = DoubaoChatRequest(
            model = endpointId,
            messages = listOf(
                DoubaoMessage("system", systemPrompt),
                DoubaoMessage("user", userPrompt)
            ),
            caching = DoubaoCaching(type = "enabled", prefix = true)
        )

        val body = json.encodeToString(DoubaoChatRequest.serializer(), requestBody)
            .toRequestBody("application/json".toMediaType())

        val trimmedKey = apiKey.trim()
        Log.d("DoubaoService", "Using API key len=${trimmedKey.length}, suffix=${trimmedKey.takeLast(6)}")
        Log.d("DoubaoService", "Chat model: $endpointId")

        val request = Request.Builder()
            .url(baseUrl)
            .post(body)
            .addHeader("Authorization", "Bearer $trimmedKey")
            .build()

        return try {
            Log.d("DoubaoService", "Sending prompt to Doubao: $userPrompt")
            val responseText = executeRequest(request)
            Log.d("DoubaoService", "RAW RESPONSE: $responseText")
            val doubaoResponse = json.decodeFromString<DoubaoChatResponse>(responseText)
            Log.d("DoubaoService", "Cache ID: ${doubaoResponse.id}")
            val rawContent = doubaoResponse.choices.firstOrNull()?.message?.content ?: return null
            
            Log.d("DoubaoService", "Raw AI Response: $rawContent")
            val jsonText = extractJson(rawContent)
            Log.d("DoubaoService", "Extracted JSON: $jsonText")
            
            json.decodeFromString<EventRequest>(jsonText)
        } catch (e: Exception) {
            Log.e("DoubaoService", "Error calling Doubao: ${e.message}", e)
            null
        }
    }

    private suspend fun executeRequest(request: Request): String = suspendCoroutine { continuation ->
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    continuation.resume(body)
                } else {
                    continuation.resumeWith(Result.failure(Exception("HTTP ${response.code}: $body")))
                }
            }
        })
    }

    private fun extractJson(text: String): String {
        val start = text.indexOf("{")
        val end = text.lastIndexOf("}")
        return if (start != -1 && end != -1) {
            text.substring(start, end + 1)
        } else {
            text
        }
    }

    suspend fun transcribeAudio(audioBytes: ByteArray): String? {
        Log.w("DoubaoService", "ASR not yet implemented for Doubao. Audio size: ${audioBytes.size}")
        return null
    }
}

