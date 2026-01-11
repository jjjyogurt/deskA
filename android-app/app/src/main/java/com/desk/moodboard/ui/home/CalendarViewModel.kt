package com.desk.moodboard.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desk.moodboard.data.model.CalendarEvent
import com.desk.moodboard.data.repository.CalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.*

enum class CalendarViewMode {
    DAY, WEEK, MONTH
}

data class CalendarUiState(
    val selectedDate: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val events: List<CalendarEvent> = emptyList()
)

class CalendarViewModel(private val calendarRepository: CalendarRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState

    init {
        refreshEvents()
    }

    fun setViewMode(mode: CalendarViewMode) {
        _uiState.value = _uiState.value.copy(viewMode = mode)
        refreshEvents()
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        refreshEvents()
    }

    fun refreshEvents() {
        viewModelScope.launch {
            val date = _uiState.value.selectedDate
            val start: LocalDateTime
            val end: LocalDateTime

            when (_uiState.value.viewMode) {
                CalendarViewMode.MONTH -> {
                    start = LocalDateTime(date.year, date.month, 1, 0, 0)
                    val daysInMonth = date.toJavaLocalDate().lengthOfMonth()
                    end = LocalDateTime(date.year, date.month, daysInMonth, 23, 59)
                }
                CalendarViewMode.WEEK -> {
                    val firstDay = date.minus(date.dayOfWeek.ordinal.toLong(), DateTimeUnit.DAY)
                    start = LocalDateTime(firstDay.year, firstDay.month, firstDay.dayOfMonth, 0, 0)
                    val lastDay = firstDay.plus(6, DateTimeUnit.DAY)
                    end = LocalDateTime(lastDay.year, lastDay.month, lastDay.dayOfMonth, 23, 59)
                }
                CalendarViewMode.DAY -> {
                    start = LocalDateTime(date.year, date.month, date.dayOfMonth, 0, 0)
                    end = LocalDateTime(date.year, date.month, date.dayOfMonth, 23, 59)
                }
            }

            val fetchedEvents = calendarRepository.getEvents(start, end)
            _uiState.value = _uiState.value.copy(events = fetchedEvents)
        }
    }

    fun next() {
        val current = _uiState.value.selectedDate
        _uiState.value = _uiState.value.copy(
            selectedDate = when (_uiState.value.viewMode) {
                CalendarViewMode.DAY -> current.plus(1, DateTimeUnit.DAY)
                CalendarViewMode.WEEK -> current.plus(1, DateTimeUnit.WEEK)
                CalendarViewMode.MONTH -> current.plus(1, DateTimeUnit.MONTH)
            }
        )
    }

    fun previous() {
        val current = _uiState.value.selectedDate
        _uiState.value = _uiState.value.copy(
            selectedDate = when (_uiState.value.viewMode) {
                CalendarViewMode.DAY -> current.minus(1, DateTimeUnit.DAY)
                CalendarViewMode.WEEK -> current.minus(1, DateTimeUnit.WEEK)
                CalendarViewMode.MONTH -> current.minus(1, DateTimeUnit.MONTH)
            }
        )
    }
}

