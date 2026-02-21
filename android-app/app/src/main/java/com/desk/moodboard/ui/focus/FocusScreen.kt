package com.desk.moodboard.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desk.moodboard.data.model.AwayTranscriptStatus
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.AccentOrange
import com.desk.moodboard.ui.theme.appBackgroundColor
import com.desk.moodboard.ui.theme.appSurfaceColor
import com.desk.moodboard.ui.theme.eInkTextColorOr
import com.desk.moodboard.ui.theme.primaryTextColor
import com.desk.moodboard.ui.theme.secondaryTextColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.lazy.items

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    viewModel: FocusViewModel = viewModel(),
    awayModeViewModel: AwayModeViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showTimerPicker by remember { mutableStateOf(false) }
    val awayUiState by awayModeViewModel.uiState.collectAsStateWithLifecycle()

    if (awayUiState.isAway) {
        AwayModeScreen(
            uiState = awayUiState,
            onBack = { awayModeViewModel.toggleAwayMode(false) },
            onOpenGreetingEditor = { awayModeViewModel.openGreetingEditor() },
            onUpdateGreetingDraft = { awayModeViewModel.updateGreetingDraft(it) },
            onRequestCloseGreetingEditor = { awayModeViewModel.requestCloseGreetingEditor() },
            onDismissDiscardDialog = { awayModeViewModel.dismissDiscardGreetingDialog() },
            onDiscardGreetingChanges = { awayModeViewModel.discardGreetingChanges() },
            onSaveGreetingDraft = { awayModeViewModel.saveGreetingDraft() },
            onToggleRecording = {
                if (awayUiState.isRecording) awayModeViewModel.stopRecordingAndSave()
                else if (awayUiState.recordingStage == RecordingStage.Idle) awayModeViewModel.startRecording()
            }
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = appBackgroundColor(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.screenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Focus",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = primaryTextColor(),
                )
                Text(
                    text = "Stay productive and focused",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor(),
                )
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TimerCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    displayTime = viewModel.getCurrentDisplayTime(),
                    isRunning = uiState.isRunning,
                    isCountdown = uiState.isCountdownMode,
                    onToggle = { viewModel.toggleFocus() },
                    onSetTimer = { showTimerPicker = true }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    FocusStatsRow()
                    DistractionCard(
                        awayUiState = awayUiState,
                        onAwayModeClick = { awayModeViewModel.toggleAwayMode(true) },
                        onToggleMessagePlayback = { awayModeViewModel.onPlayPauseMessage(it) },
                        onDeleteMessage = { awayModeViewModel.deleteMessage(it) }
                    )
                }
            }
        }
    }

    if (showTimerPicker) {
        TimerPickerBottomSheet(
            onDismiss = { showTimerPicker = false },
            onDurationSelected = { minutes ->
                viewModel.setTimer(minutes)
                showTimerPicker = false
            }
        )
    }
}

@Composable
private fun TimerCard(
    modifier: Modifier = Modifier,
    displayTime: String,
    isRunning: Boolean,
    isCountdown: Boolean,
    onToggle: () -> Unit,
    onSetTimer: () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .background(appSurfaceColor(), RoundedCornerShape(Dimens.cardCorner))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    RoundedCornerShape(Dimens.cardCorner)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (isCountdown) "FOCUS TIMER" else "CURRENT SESSION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.9.sp,
                        fontSize = 10.sp
                    ),
                    color = secondaryTextColor(),
                )
                Text(
                    text = displayTime,
                    modifier = Modifier.clickable { onSetTimer() },
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold, 
                        fontSize = 41.sp
                    ),
                    color = if (isRunning) eInkTextColorOr(AccentOrange) else primaryTextColor(),
                )
                Text(
                    text = if (isRunning) "Focusing..." else "Tap time to set goal",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = secondaryTextColor(),
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth(0.85f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color.White else AccentOrange,
                        contentColor = if (isRunning) eInkTextColorOr(AccentOrange) else eInkTextColorOr(Color.White),
                    ),
                    shape = RoundedCornerShape(6.dp),
                    border = if (isRunning) androidx.compose.foundation.BorderStroke(1.dp, AccentOrange) else null,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)
                ) {
                    Text(
                        text = if (isRunning) "Stop Focus" else "Start Focus",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimerPickerBottomSheet(
    onDismiss: () -> Unit,
    onDurationSelected: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = appSurfaceColor(),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(MaterialTheme.colorScheme.outline, CircleShape)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Set Focus Duration",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = primaryTextColor()
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(0L, 15L, 25L, 45L, 60L).forEach { mins ->
                    val label = if (mins == 0L) "Stopwatch" else "${mins}m"
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onDurationSelected(mins) },
                        shape = RoundedCornerShape(8.dp),
                        color = appBackgroundColor(),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = primaryTextColor(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusStatsRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard(title = "TODAY", value = "0m", subtitle = "Focus time")
        StatCard(title = "STREAK", value = "0", subtitle = "Days")
    }
}

@Composable
private fun RowScope.StatCard(title: String, value: String, subtitle: String) {
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .background(appSurfaceColor(), RoundedCornerShape(Dimens.cardCorner))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    RoundedCornerShape(Dimens.cardCorner)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.9.sp,
                        fontSize = 10.sp
                    ),
                    color = secondaryTextColor(),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = primaryTextColor(),
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = secondaryTextColor(),
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DistractionCard(
    awayUiState: AwayModeUiState,
    onAwayModeClick: () -> Unit,
    onToggleMessagePlayback: (VoiceMessage) -> Unit,
    onDeleteMessage: (VoiceMessage) -> Unit
) {
    val expandedRows = remember { mutableStateMapOf<String, Boolean>() }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .background(appSurfaceColor(), RoundedCornerShape(Dimens.cardCorner))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    RoundedCornerShape(Dimens.cardCorner)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VOICEMAILS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.9.sp,
                            fontSize = 10.sp
                        ),
                        color = secondaryTextColor(),
                    )

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = appBackgroundColor(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onAwayModeClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(AccentOrange, CircleShape)
                            )
                            Text(
                                text = "Set Away",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = primaryTextColor()
                            )
                        }
                    }
                }

                if (awayUiState.messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No new messages",
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryTextColor()
                        )
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = awayUiState.messages,
                            key = { it.id }
                        ) { msg ->
                            val expanded = expandedRows[msg.id] == true
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        expandedRows.remove(msg.id)
                                        onDeleteMessage(msg)
                                        true
                                    } else {
                                        false
                                    }
                                },
                                positionalThreshold = { totalDistance -> totalDistance * 0.35f }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    val isDeleteDirection = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
                                    val revealProgress = if (isDeleteDirection) dismissState.progress.coerceIn(0f, 1f) else 0f
                                    val backgroundAlpha = if (revealProgress >= 0.3f) 0.16f else 0f
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isDeleteDirection) AccentOrange.copy(alpha = backgroundAlpha) else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        if (revealProgress >= 0.3f) {
                                            Icon(
                                                painter = painterResource(id = android.R.drawable.ic_menu_delete),
                                                contentDescription = "Delete voicemail",
                                                tint = AccentOrange,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(appBackgroundColor(), RoundedCornerShape(8.dp))
                                        .clickable { expandedRows[msg.id] = !expanded }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    val isActive = awayUiState.activePlaybackId == msg.id && awayUiState.isPlaying
                                    Icon(
                                        painter = painterResource(
                                            id = if (isActive) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                                        ),
                                        contentDescription = if (isActive) "Pause" else "Play",
                                        tint = AccentOrange,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clickable { onToggleMessagePlayback(msg) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = timeFormatter.format(Date(msg.timestamp)),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = primaryTextColor()
                                        )
                                        if (expanded) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val transcriptText = when (msg.transcriptStatus) {
                                                AwayTranscriptStatus.PENDING -> "Transcribing..."
                                                AwayTranscriptStatus.FAILED -> "Transcript unavailable"
                                                AwayTranscriptStatus.READY -> msg.transcribedText?.ifBlank { "Transcript unavailable" }
                                                    ?: "Transcript unavailable"
                                            }
                                            Text(
                                                text = transcriptText,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = secondaryTextColor(),
                                                maxLines = 5,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
}
