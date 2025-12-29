package com.desk.moodboard.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.desk.moodboard.ui.theme.BackgroundGrey
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.FillGrey
import com.desk.moodboard.ui.theme.TextDark
import com.desk.moodboard.ui.theme.TextGrey
import com.desk.moodboard.ui.theme.AccentOrange
import com.desk.moodboard.ui.theme.YogurtBlue as AccentRed
import com.desk.moodboard.ui.theme.YogurtSilk
import com.desk.moodboard.ui.theme.YogurtNavy
import com.desk.moodboard.ui.theme.YogurtMint
import com.desk.moodboard.ui.theme.YogurtSilver
import com.desk.moodboard.ui.theme.YogurtGrey as OldYogurtGrey

import androidx.lifecycle.viewmodel.compose.viewModel
import com.desk.moodboard.ui.posture.PostureViewModel
import androidx.compose.runtime.collectAsState

@Composable
fun SettingsScreen(postureViewModel: PostureViewModel = viewModel()) {
    val scroll = rememberScrollState()
    val context = LocalContext.current

    var darkModeEnabled by remember { mutableStateOf(false) }
    val postureDetectionEnabled by postureViewModel.isServiceRunning.collectAsState()
    var showLogViewer by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(seedLogs()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("SettingsScreen", "Permission result CAMERA granted=$isGranted")
        if (isGranted) {
            postureViewModel.togglePostureMonitoring(true)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundGrey,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = Dimens.screenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextDark,
                )
                Text(
                    text = "Customize your experience",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextGrey,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
                ) {
                    SettingsCard(title = "Appearance") {
                        SwitchRow(
                            title = "Dark Mode",
                            subtitle = "Theme",
                            checked = darkModeEnabled,
                            onToggle = { darkModeEnabled = it }
                        )
                    }

                    SettingsCard(title = "Desk") {
                        ClickableRow(
                            title = "Height Presets",
                            value = "2 saved",
                            onClick = { showToast(context, "Coming soon") }
                        )
                        HorizontalDivider(color = FillGrey.copy(alpha = 0.4f))
                        ClickableRow(
                            title = "Sitting Reminder",
                            value = "45m",
                            onClick = { showToast(context, "Coming soon") }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
                ) {
                    SettingsCard(title = "Privacy") {
                        SwitchRow(
                            title = "Posture Monitoring",
                            subtitle = "Background",
                            checked = postureDetectionEnabled,
                            onToggle = { enable ->
                                android.util.Log.d("SettingsScreen", "Toggle Posture Monitoring -> $enable")
                                if (enable) {
                                    val status = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                                    if (status == PackageManager.PERMISSION_GRANTED) {
                                        android.util.Log.d("SettingsScreen", "Permission already granted, starting service")
                                        postureViewModel.togglePostureMonitoring(true)
                                    } else {
                                        android.util.Log.d("SettingsScreen", "Requesting CAMERA permission")
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                } else {
                                    android.util.Log.d("SettingsScreen", "Stopping service")
                                    postureViewModel.togglePostureMonitoring(false)
                                }
                            }
                        )
                    }

                    SettingsCard(title = "Debug") {
                        ValueRow(
                            title = "Entries",
                            value = logs.size.toString(),
                            valueColor = AccentOrange,
                        )
                        HorizontalDivider(color = FillGrey.copy(alpha = 0.4f))
                        ActionRow(
                            title = "View Logs",
                            hint = "→",
                            onClick = { showLogViewer = true }
                        )
                        HorizontalDivider(color = FillGrey.copy(alpha = 0.4f))
                        ActionRow(
                            title = "Clear Logs",
                            hint = "×",
                            titleColor = Color(0xFFB4371E),
                            onClick = { logs = emptyList() }
                        )
                    }

                    SettingsCard(title = "Info") {
                        ValueRow(title = "Version", value = "1.0.0", valueColor = TextGrey)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showLogViewer) {
        LogViewerDialog(
            logs = logs,
            onDismiss = { showLogViewer = false }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
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
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextDark,
                )
                content()
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = TextDark
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextGrey
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            modifier = Modifier.scale(0.8f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentOrange,
                uncheckedThumbColor = FillGrey,
                uncheckedTrackColor = FillGrey.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun ClickableRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp),
            color = TextDark,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TextGrey,
        )
    }
}

@Composable
private fun ValueRow(
    title: String,
    value: String,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp),
            color = TextDark,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = valueColor,
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    hint: String,
    titleColor: Color = TextDark,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp),
            color = titleColor,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
            color = titleColor,
        )
    }
}

@Composable
private fun LogViewerDialog(
    logs: List<DebugLogEntry>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0F1114),
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .height(520.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Debug Logs (${logs.size})",
                        style = MaterialTheme.typography.titleLarge.copy(color = YogurtSilk),
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = AccentOrange, fontSize = 15.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    logs.forEach { log ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color.White.copy(alpha = 0.04f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "[${log.level.name}]",
                                    color = log.level.color(),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = "[${log.category}]",
                                    color = TextGrey,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = log.time,
                                    color = FillGrey,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Text(
                                text = log.message,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            log.data?.let {
                                Text(
                                    text = it,
                                    color = TextGrey,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class LogLevel { ERROR, WARN, INFO, DEBUG }

private data class DebugLogEntry(
    val level: LogLevel,
    val category: String,
    val message: String,
    val time: String,
    val data: String? = null,
)

private fun LogLevel.color(): Color = when (this) {
    LogLevel.ERROR -> Color(0xFFFF6B6B)
    LogLevel.WARN -> Color(0xFFFFE66D)
    LogLevel.INFO -> Color(0xFF4ECDC4)
    LogLevel.DEBUG -> YogurtSilver
}

private fun seedLogs(): List<DebugLogEntry> = listOf(
    DebugLogEntry(
        level = LogLevel.ERROR,
        category = "Camera",
        message = "Permission denied while accessing camera",
        time = "10:21 AM",
        data = "android.permission.CAMERA not granted"
    ),
    DebugLogEntry(
        level = LogLevel.WARN,
        category = "Posture",
        message = "Low-light detection fallback triggered",
        time = "09:54 AM",
        data = "iso=800, exposure=1/30s"
    ),
    DebugLogEntry(
        level = LogLevel.INFO,
        category = "Reminder",
        message = "Standing reminder delivered",
        time = "09:30 AM",
        data = "interval=45m, channel=silent"
    ),
    DebugLogEntry(
        level = LogLevel.DEBUG,
        category = "Sync",
        message = "Calendar sync completed",
        time = "09:10 AM",
        data = "provider=Google, events=12"
    ),
    DebugLogEntry(
        level = LogLevel.INFO,
        category = "Hydration",
        message = "Water intake target updated",
        time = "08:55 AM",
        data = "goal=1800ml"
    ),
)

private fun showToast(context: android.content.Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
