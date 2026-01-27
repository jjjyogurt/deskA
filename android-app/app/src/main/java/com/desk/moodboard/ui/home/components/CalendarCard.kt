package com.desk.moodboard.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desk.moodboard.ui.home.CalendarViewModel
import com.desk.moodboard.ui.home.CalendarViewMode
import com.desk.moodboard.ui.theme.*
import kotlinx.datetime.*
import java.time.format.DateTimeFormatter
import java.util.*
import kotlinx.datetime.toJavaLocalDate

@Composable
fun CalendarCard(viewModel: CalendarViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Card(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(Dimens.cardCorner))
                .border(width = 1.dp, color = FillGrey.copy(alpha = 0.6f), shape = RoundedCornerShape(Dimens.cardCorner))
        ) {
            Column(
                modifier = Modifier.padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header with Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val headerText = when (uiState.viewMode) {
                            CalendarViewMode.MONTH -> "${uiState.selectedDate.month} ${uiState.selectedDate.year}"
                            CalendarViewMode.WEEK -> "Week of ${uiState.selectedDate.dayOfMonth} ${uiState.selectedDate.month}"
                            CalendarViewMode.DAY -> "${uiState.selectedDate.dayOfWeek}, ${uiState.selectedDate.month} ${uiState.selectedDate.dayOfMonth}"
                        }
                    Text(
                            text = headerText,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextDark
                    )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CalendarNavButton("<") { viewModel.previous() }
                        CalendarNavButton(">") { viewModel.next() }
                    }
                }

                // View Mode Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ViewModeButton("Day", uiState.viewMode == CalendarViewMode.DAY) {
                        viewModel.setViewMode(CalendarViewMode.DAY)
                    }
                    ViewModeButton("Week", uiState.viewMode == CalendarViewMode.WEEK) {
                        viewModel.setViewMode(CalendarViewMode.WEEK)
                    }
                    ViewModeButton("Month", uiState.viewMode == CalendarViewMode.MONTH) {
                        viewModel.setViewMode(CalendarViewMode.MONTH)
                    }
                }
                
                // Dynamic View Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (uiState.viewMode) {
                        CalendarViewMode.MONTH -> MonthView(uiState.selectedDate, uiState.events, viewModel)
                        CalendarViewMode.WEEK -> WeekView(uiState.selectedDate, uiState.events)
                        CalendarViewMode.DAY -> DayView(uiState.selectedDate, uiState.events)
                    }
                }

                // Selected Day Events
                if (uiState.viewMode == CalendarViewMode.MONTH) {
                    val selectedDayEvents = uiState.events.filter { it.startTime.date == uiState.selectedDate }
                    if (selectedDayEvents.isNotEmpty()) {
                        HorizontalDivider(color = FillGrey.copy(alpha = 0.3f))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            selectedDayEvents.take(2).forEach { event ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(AccentOrange, CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = event.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextDark,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) AccentOrange.copy(alpha = 0.1f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, AccentOrange) else null
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) AccentOrange else TextGrey,
                fontSize = 9.sp
            )
        )
    }
}

@Composable
private fun MonthView(
    selectedDate: LocalDate,
    events: List<com.desk.moodboard.data.model.CalendarEvent>,
    viewModel: CalendarViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Days Header
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGrey,
                    fontSize = 10.sp
                )
            }
        }
        
        val firstDayOfMonth = LocalDate(selectedDate.year, selectedDate.month, 1)
        val daysInMonth = selectedDate.toJavaLocalDate().lengthOfMonth()
        val firstDayOfWeek = (firstDayOfMonth.dayOfWeek.ordinal + 1) % 7 // Adjusted for Sunday start

        val days = mutableListOf<Int?>()
        repeat(firstDayOfWeek) { days.add(null) }
        for (i in 1..daysInMonth) { days.add(i) }
        while (days.size % 7 != 0) { days.add(null) }

        days.chunked(7).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .clickable(enabled = day != null) {
                                day?.let { viewModel.selectDate(LocalDate(selectedDate.year, selectedDate.month, it)) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (day != null) {
                            val isSelected = day == selectedDate.dayOfMonth
                            val dateAtDay = LocalDate(selectedDate.year, selectedDate.month, day)
                            val hasEvent = events.any { it.startTime.date == dateAtDay }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp, 18.dp)
                                            .background(AccentOrange, RoundedCornerShape(4.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$day",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 10.sp)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "$day",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = TextDark.copy(alpha = 0.9f),
                                        fontSize = 10.sp
                                    )
                                }
                                if (hasEvent && !isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 1.dp)
                                            .size(3.dp)
                                            .background(AccentOrange, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekView(
    selectedDate: LocalDate,
    events: List<com.desk.moodboard.data.model.CalendarEvent>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val firstDayOfWeek = selectedDate.minus(selectedDate.dayOfWeek.ordinal.toLong(), DateTimeUnit.DAY)

        (0..6).forEach { i ->
            val day = firstDayOfWeek.plus(i.toLong(), DateTimeUnit.DAY)
            val dayEvents = events.filter { it.startTime.date == day }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (day == selectedDate) AccentOrange.copy(alpha = 0.05f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = day.dayOfWeek.name.take(3),
                    modifier = Modifier.width(30.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (day == selectedDate) AccentOrange else TextGrey,
                    fontSize = 10.sp
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    modifier = Modifier.width(20.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDark,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (dayEvents.isNotEmpty()) {
                    Text(
                        text = dayEvents.first().title,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGrey,
                        fontSize = 9.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(FillGrey.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

@Composable
private fun DayView(
    selectedDate: LocalDate,
    events: List<com.desk.moodboard.data.model.CalendarEvent>
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        (8..20).forEach { hour ->
            val hourEvents = events.filter { it.startTime.hour == hour }
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("%02d:00", hour),
                    modifier = Modifier.width(40.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGrey,
                    fontSize = 10.sp
                )
                if (hourEvents.isNotEmpty()) {
                    Card(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = hourEvents.first().title,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = AccentOrange,
                                fontSize = 9.sp
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(FillGrey.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarNavButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(24.dp),
        shape = RoundedCornerShape(4.dp),
        color = FillGrey.copy(alpha = 0.4f)
    ) {
        Box(contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextDark,
            fontSize = 11.sp
        )
        }
    }
}



