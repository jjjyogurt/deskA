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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.desk.moodboard.R
import com.desk.moodboard.ui.home.CalendarViewModel
import com.desk.moodboard.ui.home.CalendarViewMode
import com.desk.moodboard.ui.theme.*
import kotlinx.datetime.*
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.*
import kotlinx.datetime.toJavaLocalDate

@Composable
fun CalendarCard(viewModel: CalendarViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var hasPermission by remember { mutableStateOf(false) }
    var permissionChecked by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val hasRead = grants[Manifest.permission.READ_CALENDAR] == true
        val hasWrite = grants[Manifest.permission.WRITE_CALENDAR] == true
        hasPermission = hasRead && hasWrite
        permissionChecked = true
        if (hasPermission) {
            viewModel.refreshEvents()
        }
    }

    LaunchedEffect(Unit) {
        val hasRead = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        val hasWrite = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        hasPermission = hasRead && hasWrite
        permissionChecked = true
        if (hasPermission) {
            viewModel.refreshEvents()
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth().height(300.dp),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(appSurfaceColor(), RoundedCornerShape(Dimens.cardCorner))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(Dimens.cardCorner)
                )
        ) {
            Column(
                modifier = Modifier.padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!permissionChecked || !hasPermission) {
                    Text(
                        text = stringResource(R.string.home_calendar_access_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = primaryTextColor()
                    )
                    Text(
                        text = stringResource(R.string.home_calendar_access_body),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryTextColor()
                    )
                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_CALENDAR,
                                    Manifest.permission.WRITE_CALENDAR
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentOrange,
                            contentColor = eInkTextColorOr(Color.White),
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_calendar_enable),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                } else {
                    // Header with Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val locale = Locale.getDefault()
                            val selectedJavaDate = uiState.selectedDate.toJavaLocalDate()
                            val headerText = when (uiState.viewMode) {
                                CalendarViewMode.MONTH -> selectedJavaDate.format(
                                    DateTimeFormatter.ofPattern("LLLL yyyy", locale)
                                )
                                CalendarViewMode.WEEK -> stringResource(
                                    R.string.home_calendar_header_week,
                                    uiState.selectedDate.dayOfMonth,
                                    uiState.selectedDate.month.getDisplayName(TextStyle.SHORT, locale)
                                )
                                CalendarViewMode.DAY -> stringResource(
                                    R.string.home_calendar_header_day,
                                    uiState.selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, locale),
                                    uiState.selectedDate.month.getDisplayName(TextStyle.SHORT, locale),
                                    uiState.selectedDate.dayOfMonth
                                )
                            }
                            Text(
                                text = headerText,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = primaryTextColor()
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            CalendarNavButton(stringResource(R.string.home_calendar_prev)) { viewModel.previous() }
                            CalendarNavButton(stringResource(R.string.home_calendar_next)) { viewModel.next() }
                        }
                    }

                    // View Mode Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ViewModeButton(stringResource(R.string.home_calendar_view_day), uiState.viewMode == CalendarViewMode.DAY) {
                            viewModel.setViewMode(CalendarViewMode.DAY)
                        }
                        ViewModeButton(stringResource(R.string.home_calendar_view_week), uiState.viewMode == CalendarViewMode.WEEK) {
                            viewModel.setViewMode(CalendarViewMode.WEEK)
                        }
                        ViewModeButton(stringResource(R.string.home_calendar_view_month), uiState.viewMode == CalendarViewMode.MONTH) {
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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                selectedDayEvents.take(2).forEach { event ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(6.dp).background(AccentOrange, CircleShape))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = event.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = primaryTextColor(),
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
                color = if (isSelected) AccentOrange else secondaryTextColor(),
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
            val locale = Locale.getDefault()
            val days = listOf(
                DayOfWeek.SUNDAY,
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
                DayOfWeek.SATURDAY
            )
            days.forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, locale),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor(),
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
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = eInkTextColorOr(Color.White),
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "$day",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                        color = primaryTextColor(),
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
                    text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    modifier = Modifier.width(30.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (day == selectedDate) AccentOrange else secondaryTextColor(),
                    fontSize = 10.sp
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    modifier = Modifier.width(20.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryTextColor(),
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (dayEvents.isNotEmpty()) {
                    Text(
                        text = dayEvents.first().title,
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryTextColor(),
                        fontSize = 9.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
                    color = secondaryTextColor(),
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
                                color = eInkTextColorOr(AccentOrange),
                                fontSize = 9.sp
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    ) {
        Box(contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = primaryTextColor(),
            fontSize = 11.sp
        )
        }
    }
}




