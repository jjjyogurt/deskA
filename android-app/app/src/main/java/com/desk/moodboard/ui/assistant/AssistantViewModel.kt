package com.desk.moodboard.ui.assistant

import com.desk.moodboard.data.ble.RemoteBleRepository
import com.desk.moodboard.data.model.ChatMessage
import com.desk.moodboard.data.remote.DoubaoService
import com.desk.moodboard.data.repository.CalendarRepository
import com.desk.moodboard.data.repository.NoteRepository
import com.desk.moodboard.data.repository.TodoRepository
import com.desk.moodboard.domain.ConflictDetector
import com.desk.moodboard.ui.home.CalendarViewModel
import com.desk.moodboard.voice.AudioRecorder
import com.desk.moodboard.voice.VolcengineASRService

data class AssistantUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isRecording: Boolean = false,
    val isLoading: Boolean = false,
    val currentTranscript: String = "",
    val error: String? = null,
    val showSuccessCheck: Boolean = false
)

class AssistantViewModel(
    doubaoService: DoubaoService?,
    todoRepository: TodoRepository,
    noteRepository: NoteRepository,
    calendarRepository: CalendarRepository,
    audioRecorder: AudioRecorder,
    volcengineASRService: VolcengineASRService,
    conflictDetector: ConflictDetector,
    calendarViewModel: CalendarViewModel,
    remoteBleRepository: RemoteBleRepository,
) : VoiceAgentViewModel(
    doubaoService = doubaoService,
    todoRepository = todoRepository,
    noteRepository = noteRepository,
    calendarRepository = calendarRepository,
    audioRecorder = audioRecorder,
    volcengineASRService = volcengineASRService,
    conflictDetector = conflictDetector,
    calendarViewModel = calendarViewModel,
    remoteBleRepository = remoteBleRepository,
)
