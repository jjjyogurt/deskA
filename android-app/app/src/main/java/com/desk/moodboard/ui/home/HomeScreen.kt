package com.desk.moodboard.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desk.moodboard.ui.assistant.AssistantViewModel
import com.desk.moodboard.ui.home.components.*
import com.desk.moodboard.ui.theme.*
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(
    assistantViewModel: AssistantViewModel = koinViewModel(),
    todoViewModel: TodoViewModel = koinViewModel(),
    calendarViewModel: CalendarViewModel = koinInject()
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundGrey,
        ) {
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = Dimens.screenPadding, vertical = 12.dp)
            ) {
                // Header with Date/Time
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentDateTime = LocalDateTime.now()
                    val formatter = DateTimeFormatter.ofPattern("E, MMM d h:mm a", Locale.ENGLISH)
                    Text(
                        text = currentDateTime.format(formatter),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp
                        ),
                        color = TextGrey
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing)
                ) {
                    // Left Column
                    Column(
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing)
                    ) {
                        MoodboardCard(assistantViewModel)
                        TodoCard(todoViewModel)
                        NotesCard(todoViewModel)
                        HealthScoreCard()
                    }

                    // Right Column
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing)
                    ) {
                        CalendarCard(calendarViewModel)
                        // VoiceAgentCard removed from grid
                    }
                }
                
                // Extra padding at bottom to ensure content isn't hidden by the floating pill
                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        // Floating Voice Agent Strip
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
                .imePadding() // Ensure it moves up with keyboard
        ) {
            FloatingVoiceAgent(assistantViewModel)
        }
    }
}
