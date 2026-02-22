package com.desk.moodboard.ui.assistant

import android.util.Log
import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desk.moodboard.R
import com.desk.moodboard.data.model.AssistantIntentType
import com.desk.moodboard.data.model.ChatMessage
import com.desk.moodboard.data.model.EventAction
import com.desk.moodboard.data.model.EventRequest
import com.desk.moodboard.data.model.EventType
import com.desk.moodboard.data.model.NoteRequest
import com.desk.moodboard.data.model.TodoRequest
import com.desk.moodboard.data.ble.RemoteAudioPacket
import com.desk.moodboard.data.ble.RemoteBleRepository
import com.desk.moodboard.data.ble.RemoteMicEvent
import com.desk.moodboard.data.remote.DoubaoService
import com.desk.moodboard.data.repository.CalendarCreateResult
import com.desk.moodboard.data.repository.CalendarRepository
import com.desk.moodboard.data.repository.NoteRepository
import com.desk.moodboard.data.repository.TodoRepository
import com.desk.moodboard.domain.ConflictDetector
import com.desk.moodboard.ui.home.CalendarViewModel
import com.desk.moodboard.voice.AudioRecorder
import com.desk.moodboard.voice.VolcengineASRService
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.sqrt

open class VoiceAgentViewModel(
    private val appContext: Context,
    private val doubaoService: DoubaoService?,
    private val todoRepository: TodoRepository,
    private val noteRepository: NoteRepository,
    private val calendarRepository: CalendarRepository,
    private val audioRecorder: AudioRecorder,
    private val volcengineASRService: VolcengineASRService,
    private val conflictDetector: ConflictDetector,
    private val calendarViewModel: CalendarViewModel,
    private val remoteBleRepository: RemoteBleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState

    private var asrInitialized = false
    private val remoteMicLock = Any()
    private var remoteSessionCounter = 0L
    private var remoteSessionId = 0L
    private var remoteSessionActive = false
    private var remoteStopReceived = false
    private var remoteLastPacketReceived = false
    private var expectedPacketSequence = 0
    private var missingPacketCount = 0
    private var receivedPacketCount = 0
    private var remotePcmBuffer = ByteArrayOutputStream()
    private var remoteFinalizeTimeoutJob: Job? = null

    init {
        addMessage(s(R.string.assistant_greeting), false)
        observeRemoteMicEvents()
        observeRemoteAudioPackets()
    }

    fun onSendMessage(text: String) {
        if (text.isBlank()) return
        addMessage(text, true)
        processTextInput(text)
    }

    fun onToggleRecording(context: android.content.Context) {
        viewModelScope.launch {
            if (!asrInitialized) {
                val initialized = audioRecorder.initialize()
                if (!initialized) {
                    addMessage(s(R.string.assistant_error_microphone_permission), false)
                    return@launch
                }
                asrInitialized = true
            }

            if (_uiState.value.isRecording) {
                _uiState.value = _uiState.value.copy(isRecording = false, isLoading = true)
                val pcm = audioRecorder.stopRecordingRawPcm()
                val finalTranscript = withTimeoutOrNull(AsrTimeoutMs) {
                    volcengineASRService.transcribePcm(pcm)
                } ?: ""
                _uiState.value = _uiState.value.copy(isLoading = false, currentTranscript = "")

                if (finalTranscript.isNotBlank()) {
                    addMessage(finalTranscript, true)
                    processTextInput(finalTranscript)
                } else {
                    addMessage(s(R.string.assistant_error_could_not_hear), false)
                }
            } else {
                _uiState.value = _uiState.value.copy(isRecording = true, currentTranscript = "")
                audioRecorder.startRecording()
            }
        }
    }

    private fun processTextInput(text: String) {
        if (doubaoService == null) {
            addMessage(s(R.string.assistant_error_api_key_missing), false)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val normalized = text.trim()
            Log.d("VoiceAgent", "Input: $normalized")

            val intent = withTimeoutOrNull(LlmTimeoutMs) {
                try {
                    Log.d("VoiceAgent", "Calling parseAssistantIntent()")
                    doubaoService.parseAssistantIntent(normalized)
                } catch (error: Exception) {
                    Log.e("VoiceAgent", "parseAssistantIntent failed", error)
                    null
                }
            }
            Log.d("VoiceAgent", "IntentResult=$intent")
            if (intent == null) {
                val earlyIntentHandled = handleEarlyIntent(normalized)
                Log.d("VoiceAgent", "EarlyIntentHandled=$earlyIntentHandled")
                if (earlyIntentHandled) {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }
                addMessage(s(R.string.assistant_error_trouble_now), false)
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            if (intent.needsClarification) {
                addMessage(intent.clarificationQuestion ?: s(R.string.todo_could_you_clarify), false)
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }

            when (intent.intentType) {
                AssistantIntentType.TODO -> handleTodo(intent.todo)
                AssistantIntentType.NOTE -> handleNote(intent.note)
                AssistantIntentType.EVENT -> handleEventRequest(intent.event)
                AssistantIntentType.CHAT -> addMessage(intent.chatResponse ?: s(R.string.todo_how_can_i_help), false)
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    private fun observeRemoteMicEvents() {
        viewModelScope.launch {
            remoteBleRepository.remoteMicEvents.collect { event ->
                when (event) {
                    RemoteMicEvent.Started -> handleRemoteMicStarted()
                    RemoteMicEvent.Stopped -> handleRemoteMicStopped()
                }
            }
        }
    }

    private fun observeRemoteAudioPackets() {
        viewModelScope.launch {
            remoteBleRepository.remoteAudioPackets.collect { packet ->
                handleRemoteAudioPacket(packet)
            }
        }
    }

    private fun handleRemoteMicStarted() {
        val sessionId = synchronized(remoteMicLock) {
            remoteSessionCounter += 1
            remoteSessionId = remoteSessionCounter
            remoteSessionActive = true
            remoteStopReceived = false
            remoteLastPacketReceived = false
            expectedPacketSequence = 0
            missingPacketCount = 0
            receivedPacketCount = 0
            remotePcmBuffer.reset()
            remoteSessionId
        }
        remoteFinalizeTimeoutJob?.cancel()
        remoteFinalizeTimeoutJob = null
        Log.i("VoiceAgent", "Remote mic started session=$sessionId")
        _uiState.value = _uiState.value.copy(isRecording = true, isLoading = false, currentTranscript = "")
    }

    private fun handleRemoteMicStopped() {
        val shouldFinalizeNow = synchronized(remoteMicLock) {
            if (!remoteSessionActive) {
                Log.w("VoiceAgent", "Remote mic stop ignored: no active session")
                return@synchronized false
            }
            remoteStopReceived = true
            remoteLastPacketReceived
        }
        Log.i("VoiceAgent", "Remote mic stopped session=$remoteSessionId")
        if (shouldFinalizeNow) {
            finalizeRemoteSessionAndTranscribe()
        } else {
            _uiState.value = _uiState.value.copy(isRecording = false, isLoading = true)
            scheduleRemoteFinalizeFallback("STOP_EVENT")
        }
    }

    private fun handleRemoteAudioPacket(packet: RemoteAudioPacket) {
        var shouldFinalize = false
        var shouldRescheduleFallback = false
        synchronized(remoteMicLock) {
            if (!remoteSessionActive) {
                Log.w("VoiceAgent", "Dropping remote audio packet; no active session seq=${packet.sequence}")
                return
            }
            if (packet.sequence < expectedPacketSequence) {
                Log.w(
                    "VoiceAgent",
                    "Dropping out-of-order packet seq=${packet.sequence} expected=$expectedPacketSequence"
                )
                return
            }
            if (packet.sequence > expectedPacketSequence) {
                missingPacketCount += packet.sequence - expectedPacketSequence
            }
            expectedPacketSequence = packet.sequence + 1
            receivedPacketCount += 1
            if (packet.pcmBytes.isNotEmpty()) {
                remotePcmBuffer.write(packet.pcmBytes)
            }
            if (packet.isLast) {
                remoteLastPacketReceived = true
            }
            shouldFinalize = remoteStopReceived && remoteLastPacketReceived
            shouldRescheduleFallback = remoteStopReceived && !remoteLastPacketReceived
        }
        if (shouldRescheduleFallback) {
            scheduleRemoteFinalizeFallback("POST_STOP_PACKET seq=${packet.sequence}")
        }
        if (shouldFinalize) {
            finalizeRemoteSessionAndTranscribe()
        }
    }

    private fun scheduleRemoteFinalizeFallback(reason: String) {
        remoteFinalizeTimeoutJob?.cancel()
        remoteFinalizeTimeoutJob = viewModelScope.launch {
            delay(RemotePostStopIdleTimeoutMs)
            val shouldFinalizeByTimeout = synchronized(remoteMicLock) {
                remoteSessionActive && remoteStopReceived && !remoteLastPacketReceived
            }
            if (shouldFinalizeByTimeout) {
                Log.w("VoiceAgent", "Remote session fallback finalize triggered reason=$reason")
                finalizeRemoteSessionAndTranscribe()
            }
        }
    }

    private fun finalizeRemoteSessionAndTranscribe() {
        remoteFinalizeTimeoutJob?.cancel()
        remoteFinalizeTimeoutJob = null
        val snapshot = synchronized(remoteMicLock) {
            if (!remoteSessionActive) {
                return
            }
            val bytes = remotePcmBuffer.toByteArray()
            val packetCount = receivedPacketCount
            val missingCount = missingPacketCount
            val session = remoteSessionId
            remoteSessionActive = false
            remoteStopReceived = false
            remoteLastPacketReceived = false
            expectedPacketSequence = 0
            missingPacketCount = 0
            receivedPacketCount = 0
            remotePcmBuffer = ByteArrayOutputStream()
            RemoteSessionSnapshot(
                sessionId = session,
                pcmBytes = bytes,
                packetCount = packetCount,
                missingCount = missingCount,
            )
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRecording = false, isLoading = true)
            val estimatedDurationMs = estimatePcmDurationMs(snapshot.pcmBytes.size)
            Log.i(
                "VoiceAgent",
                "Remote session finalized session=${snapshot.sessionId} packets=${snapshot.packetCount} " +
                    "missing=${snapshot.missingCount} bytes=${snapshot.pcmBytes.size} " +
                    "durationMs=$estimatedDurationMs"
            )

            if (snapshot.pcmBytes.isEmpty()) {
                addMessage(s(R.string.assistant_error_could_not_hear), false)
                _uiState.value = _uiState.value.copy(isLoading = false, currentTranscript = "")
                return@launch
            }

            val pcmSamples = toLittleEndianShortArray(snapshot.pcmBytes)
            val diagnostics = buildPcmDiagnostics(pcmSamples)
            Log.i(
                "VoiceAgent",
                "Remote audio diagnostics session=${snapshot.sessionId} samples=${pcmSamples.size} " +
                    "rms=${diagnostics.rms} peak=${diagnostics.peak}"
            )
            val finalTranscript = withTimeoutOrNull(AsrTimeoutMs) {
                volcengineASRService.transcribePcm(pcmSamples)
            } ?: ""
            _uiState.value = _uiState.value.copy(isLoading = false, currentTranscript = "")

            if (finalTranscript.isNotBlank()) {
                addMessage(finalTranscript, true)
                processTextInput(finalTranscript)
            } else {
                addMessage(s(R.string.assistant_error_could_not_hear), false)
            }
        }
    }

    private fun toLittleEndianShortArray(bytes: ByteArray): ShortArray {
        if (bytes.size < 2) {
            return ShortArray(0)
        }
        val sampleCount = bytes.size / 2
        val output = ShortArray(sampleCount)
        var offset = 0
        for (index in 0 until sampleCount) {
            val low = bytes[offset].toInt() and 0xFF
            val high = bytes[offset + 1].toInt() and 0xFF
            output[index] = ((high shl 8) or low).toShort()
            offset += 2
        }
        return output
    }

    private fun estimatePcmDurationMs(byteCount: Int): Long {
        val bytesPerSecond = RemoteAsrSampleRateHz * RemoteAsrChannelCount * RemoteAsrBytesPerSample
        if (bytesPerSecond <= 0) {
            return 0L
        }
        return (byteCount.toLong() * 1000L) / bytesPerSecond.toLong()
    }

    private fun buildPcmDiagnostics(samples: ShortArray): PcmDiagnostics {
        if (samples.isEmpty()) {
            return PcmDiagnostics(rms = 0.0, peak = 0)
        }
        var peak = 0
        var sumSquares = 0.0
        for (sample in samples) {
            val value = sample.toInt()
            val absolute = abs(value)
            if (absolute > peak) {
                peak = absolute
            }
            sumSquares += value.toDouble() * value.toDouble()
        }
        val rms = sqrt(sumSquares / samples.size.toDouble())
        return PcmDiagnostics(rms = rms, peak = peak)
    }

    private fun handleEarlyIntent(text: String): Boolean {
        val normalized = text.lowercase()
        val containsDateTime = containsDateOrTimeKeyword(normalized)
        val containsEventKeyword = containsAnyKeyword(normalized, EventKeywords)
        val containsNoteKeyword = containsAnyKeyword(normalized, NoteKeywords)
        val containsTodoKeyword = containsAnyKeyword(normalized, TodoKeywords)

        if (containsNoteKeyword && !containsEventKeyword) {
            val note = NoteRequest(
                title = deriveShortTitle("", text),
                content = text,
                confidence = 0.5f
            )
            viewModelScope.launch { handleNote(note) }
            return true
        }

        if (containsTodoKeyword && !containsEventKeyword && !containsNoteKeyword && !containsDateTime) {
            val title = stripTodoPrefix(text).ifBlank { text }
            val todo = TodoRequest(
                title = title,
                description = "",
                createCalendarEvent = false,
                confidence = 0.5f
            )
            viewModelScope.launch { handleTodo(todo) }
            return true
        }

        return false
    }

    private suspend fun handleTodo(todo: TodoRequest?) {
        if (todo == null || todo.title.isBlank()) {
            addMessage(s(R.string.assistant_prompt_tell_todo), false)
            return
        }

        try {
            todoRepository.insertFromRequest(todo)
            addMessage(s(R.string.assistant_todo_added, todo.title), false)
            triggerSuccessFeedback()
        } catch (error: Exception) {
            addMessage(s(R.string.assistant_error_add_todo_failed), false)
            return
        }

        if (!todo.createCalendarEvent) return

        val date = todo.dueDate
        val time = todo.dueTime
        if (date == null || time == null) {
            addMessage(s(R.string.assistant_prompt_todo_calendar_time), false)
            return
        }

        val startTime = LocalDateTime(
            date.year,
            date.month,
            date.dayOfMonth,
            time.hour,
            time.minute,
            time.second
        )

        val eventRequest = EventRequest(
            action = EventAction.CREATE,
            title = todo.title,
            description = todo.description,
            startTime = startTime,
            duration = DefaultEventDurationMinutes,
            eventType = EventType.TASK,
            confidence = todo.confidence
        )

        handleEventRequest(eventRequest)
    }

    private suspend fun handleNote(note: NoteRequest?) {
        val content = note?.content?.trim().orEmpty()
        if (content.isBlank()) {
            addMessage(s(R.string.assistant_prompt_say_idea_again), false)
            return
        }

        val shortTitle = deriveShortTitle(note?.title.orEmpty(), content)
        val sanitized = (note ?: NoteRequest()).copy(title = shortTitle, content = content)

        try {
            noteRepository.insertFromRequest(sanitized, null)
            addMessage(s(R.string.assistant_note_saved, shortTitle), false)
            triggerSuccessFeedback()
        } catch (error: Exception) {
            addMessage(s(R.string.assistant_error_save_note_failed), false)
        }
    }

    private suspend fun handleEventRequest(request: EventRequest?) {
        if (request == null) {
            addMessage(s(R.string.assistant_prompt_repeat), false)
            return
        }
        if (request.needsClarification) {
            addMessage(request.clarificationQuestion ?: s(R.string.todo_could_you_clarify), false)
            return
        }
        if (request.action == EventAction.CREATE && request.startTime == null) {
            addMessage(s(R.string.assistant_prompt_schedule_time), false)
            return
        }

        when (request.action) {
            EventAction.CHAT -> addMessage(request.chatResponse ?: s(R.string.todo_how_can_i_help), false)
            EventAction.CREATE -> {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val end = now.plusDays(DefaultConflictWindowDays)
                val existingEvents = calendarRepository.getEvents(now, end)
                val derivedTitle = if (request.title.isBlank()) {
                    s(R.string.assistant_new_event_title, UUID.randomUUID().toString().take(4))
                } else {
                    request.title
                }
                val conflict = conflictDetector.detectConflicts(request.copy(title = derivedTitle), existingEvents)

                if (conflict.hasConflict) {
                    addMessage(s(R.string.assistant_conflict, conflict.reasoning), false)
                    return
                }

                val finalRequest = request.copy(title = derivedTitle)
                when (val createResult = calendarRepository.createEvent(finalRequest)) {
                    is CalendarCreateResult.Success -> {
                        addMessage(s(R.string.assistant_scheduled, finalRequest.title), false)
                        triggerSuccessFeedback()
                        finalRequest.startTime?.let { calendarViewModel.selectDate(it.date) }
                        calendarViewModel.refreshEvents()
                    }
                    is CalendarCreateResult.PermissionDenied -> {
                        addMessage(s(R.string.assistant_calendar_permission_required), false)
                    }
                    is CalendarCreateResult.NoWritableCalendar -> {
                        addMessage(s(R.string.assistant_error_no_writable_calendar), false)
                    }
                    is CalendarCreateResult.InvalidInput -> {
                        addMessage(createResult.reason, false)
                    }
                    is CalendarCreateResult.ProviderError -> {
                        addMessage(
                            s(
                                R.string.assistant_error_schedule_failed,
                                createResult.reason ?: s(R.string.assistant_try_again)
                            ),
                            false
                        )
                    }
                }
            }
            EventAction.LIST -> {
                val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                val events = calendarRepository.getEvents(now, now.plusDays(1))
                if (events.isEmpty()) {
                    addMessage(s(R.string.assistant_no_events_today), false)
                } else {
                    val list = events.joinToString("\n") { "- ${it.title} at ${it.startTime.time}" }
                    addMessage(s(R.string.assistant_todays_events, list), false)
                }
            }
            else -> addMessage(s(R.string.assistant_not_supported), false)
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + ChatMessage(UUID.randomUUID().toString(), text, isUser)
        )
    }

    private fun triggerSuccessFeedback() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(showSuccessCheck = true)
            delay(1500)
            _uiState.value = _uiState.value.copy(showSuccessCheck = false)
        }
    }

    private fun deriveShortTitle(title: String, content: String): String {
        val cleanedTitle = title.trim()
        val cleanedContent = content.trim()
        val hasWhitespace = cleanedContent.any { it.isWhitespace() }
        val words = cleanedContent.split(Regex("\\s+")).filter { it.isNotBlank() }

        val candidate = when {
            cleanedTitle.isNotBlank() -> cleanedTitle
            hasWhitespace -> words.take(MaxNoteTitleWords).joinToString(" ")
            else -> cleanedContent.take(MaxNoteTitleChars)
        }

        val sanitized = candidate.trim()
        return if (sanitized.isNotBlank()) sanitized else s(R.string.assistant_default_note_title)
    }

    private fun stripTodoPrefix(text: String): String {
        val normalized = text.trim()
        val prefixes = listOf("todo:", "todo ", "to-do:", "to-do ", "task:", "task ", "add todo ", "add task ")
        val matched = prefixes.firstOrNull { normalized.lowercase().startsWith(it) }
        return matched?.let { normalized.drop(it.length).trim() } ?: normalized
    }

    private fun containsDateOrTimeKeyword(normalized: String): Boolean {
        if (DateTimeRegex.containsMatchIn(normalized)) return true
        return containsAnyKeyword(normalized, DateTimeKeywords)
    }

    private fun containsAnyKeyword(text: String, keywords: List<String>): Boolean {
        return keywords.any { text.contains(it) }
    }

    private fun LocalDateTime.plusDays(days: Int): LocalDateTime {
        val endDate = this.date.plus(days, DateTimeUnit.DAY)
        return LocalDateTime(endDate, this.time)
    }

    companion object {
        private const val AsrTimeoutMs = 10000L
        private const val LlmTimeoutMs = 8000L
        private const val RemotePostStopIdleTimeoutMs = 2000L
        private const val RemoteAsrSampleRateHz = 16000
        private const val RemoteAsrChannelCount = 1
        private const val RemoteAsrBytesPerSample = 2
        private const val DefaultConflictWindowDays = 7
        private const val DefaultEventDurationMinutes = 60
        private const val MaxNoteTitleWords = 5
        private const val MaxNoteTitleChars = 16

        private val DateTimeRegex = Regex("\\b\\d{1,2}:\\d{2}\\b|\\b\\d{4}-\\d{2}-\\d{2}\\b")
        private val DateTimeKeywords = listOf(
            "today", "tomorrow", "tonight", "this week", "next week",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "今天", "明天", "今晚", "本周", "下周", "周一", "周二", "周三", "周四", "周五", "周六", "周日"
        )
        private val NoteKeywords = listOf(
            "note", "idea", "idea notes", "write it down", "remember this", "jot this down",
            "capture this", "save this", "log this", "record this", "document this", "put this in notes",
            "记录一个想法", "想法", "灵感", "把这个记下来", "记下来", "写下来", "存为笔记", "保存为笔记", "idea notes"
        )
        private val TodoKeywords = listOf(
            "todo", "to-do", "task", "add todo", "add task", "remind me to"
        )
        private val EventKeywords = listOf(
            "schedule", "meeting", "calendar", "appointment", "set a reminder", "reminder",
            "安排", "会议", "日历", "预约", "提醒"
        )
    }

    private fun s(@StringRes resId: Int, vararg args: Any): String {
        return appContext.getString(resId, *args)
    }
}

private data class RemoteSessionSnapshot(
    val sessionId: Long,
    val pcmBytes: ByteArray,
    val packetCount: Int,
    val missingCount: Int,
)

private data class PcmDiagnostics(
    val rms: Double,
    val peak: Int,
)

