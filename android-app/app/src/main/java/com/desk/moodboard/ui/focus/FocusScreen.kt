package com.desk.moodboard.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desk.moodboard.ui.theme.BackgroundGrey
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.FillGrey
import com.desk.moodboard.ui.theme.TextDark
import com.desk.moodboard.ui.theme.TextGrey
import com.desk.moodboard.ui.theme.AccentOrange
import com.desk.moodboard.ui.theme.YogurtSilk
import com.desk.moodboard.ui.theme.YogurtNavy
import com.desk.moodboard.ui.theme.YogurtGrey as OldYogurtGrey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(viewModel: FocusViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    var showTimerPicker by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundGrey,
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
                    color = TextDark,
                )
                Text(
                    text = "Stay productive and focused",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextGrey,
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
                    DistractionCard()
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
                .background(Color.White, RoundedCornerShape(Dimens.cardCorner))
                .border(1.dp, FillGrey.copy(alpha = 0.6f), RoundedCornerShape(Dimens.cardCorner))
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
                    color = TextGrey,
                )
                Text(
                    text = displayTime,
                    modifier = Modifier.clickable { onSetTimer() },
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold, 
                        fontSize = 41.sp
                    ),
                    color = if (isRunning) AccentOrange else TextDark,
                )
                Text(
                    text = if (isRunning) "Focusing..." else "Tap time to set goal",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextGrey,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth(0.85f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) Color.White else AccentOrange,
                        contentColor = if (isRunning) AccentOrange else Color.White,
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
        containerColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(FillGrey, CircleShape)
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
                color = TextDark
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
                        color = BackgroundGrey,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FillGrey.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = TextDark,
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
                .background(Color.White, RoundedCornerShape(Dimens.cardCorner))
                .border(1.dp, FillGrey.copy(alpha = 0.6f), RoundedCornerShape(Dimens.cardCorner))
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
                    color = TextGrey,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextDark,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextGrey,
                )
            }
        }
    }
}

@Composable
private fun DistractionCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(Dimens.cardCorner))
                .border(1.dp, FillGrey.copy(alpha = 0.6f), RoundedCornerShape(Dimens.cardCorner))
        ) {
            Column(
                modifier = Modifier.padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Distractions",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextDark,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(AccentOrange, CircleShape),
                    )
                    Text(
                        text = "None detected",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextDark,
                    )
                }
                Text(
                    text = "Tracked during sessions",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextGrey,
                )
            }
        }
    }
}
