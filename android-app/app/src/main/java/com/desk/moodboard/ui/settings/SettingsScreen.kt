package com.desk.moodboard.ui.settings

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.AccentOrange
import com.desk.moodboard.ui.theme.YogurtBlue as AccentRed
import com.desk.moodboard.ui.theme.YogurtSilk
import com.desk.moodboard.ui.theme.YogurtNavy
import com.desk.moodboard.ui.theme.YogurtMint
import com.desk.moodboard.ui.theme.YogurtSilver
import com.desk.moodboard.ui.theme.YogurtGrey as OldYogurtGrey
import com.desk.moodboard.ui.theme.appBackgroundColor
import com.desk.moodboard.ui.theme.appSurfaceColor
import com.desk.moodboard.ui.theme.eInkTextColorOr
import com.desk.moodboard.ui.theme.primaryTextColor
import com.desk.moodboard.ui.theme.secondaryTextColor
import org.koin.androidx.compose.koinViewModel

import androidx.lifecycle.viewmodel.compose.viewModel
import com.desk.moodboard.R
import com.desk.moodboard.i18n.AppLanguage
import com.desk.moodboard.ui.posture.PostureViewModel
import androidx.compose.runtime.collectAsState

@Composable
fun SettingsScreen(
    postureViewModel: PostureViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    onApplyLanguage: (AppLanguage) -> Unit = {},
) {
    val scroll = rememberScrollState()
    val context = LocalContext.current

    var darkModeEnabled by remember { mutableStateOf(false) }
    val postureDetectionEnabled by postureViewModel.isServiceRunning.collectAsState()
    val eInkEnabled by settingsViewModel.eInkEnabled.collectAsStateWithLifecycle()
    val appLanguage by settingsViewModel.appLanguage.collectAsStateWithLifecycle()
    var showLogViewer by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(seedLogs()) }
    var isApplyingLanguage by remember { mutableStateOf(false) }
    val languageTransitionAlpha by animateFloatAsState(
        targetValue = if (isApplyingLanguage) 0.08f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "languageTransitionAlpha",
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        android.util.Log.d("SettingsScreen", "Permission result CAMERA granted=$isGranted")
        if (isGranted) {
            postureViewModel.togglePostureMonitoring(true)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = appBackgroundColor(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(horizontal = Dimens.screenPadding, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = primaryTextColor(),
                        )
                        Text(
                            text = stringResource(R.string.settings_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryTextColor(),
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
                            SettingsCard(title = stringResource(R.string.settings_section_appearance)) {
                                SwitchRow(
                                    title = stringResource(R.string.settings_eink_mode),
                                    subtitle = stringResource(R.string.settings_eink_subtitle),
                                    checked = eInkEnabled,
                                    onToggle = { settingsViewModel.setEInk(it) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                ClickableRow(
                                    title = stringResource(R.string.language_label),
                                    value = currentLanguageLabel(appLanguage),
                                    onClick = { showLanguageDialog = true }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                SwitchRow(
                                    title = stringResource(R.string.settings_dark_mode),
                                    subtitle = stringResource(R.string.settings_dark_mode_subtitle),
                                    checked = darkModeEnabled,
                                    onToggle = { darkModeEnabled = it }
                                )
                            }

                            SettingsCard(title = stringResource(R.string.settings_section_desk)) {
                                ClickableRow(
                                    title = stringResource(R.string.settings_height_presets),
                                    value = stringResource(R.string.settings_height_presets_value),
                                    onClick = { showToast(context, context.getString(R.string.settings_coming_soon)) }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                ClickableRow(
                                    title = stringResource(R.string.settings_sitting_reminder),
                                    value = stringResource(R.string.settings_sitting_reminder_value),
                                    onClick = { showToast(context, context.getString(R.string.settings_coming_soon)) }
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
                        ) {
                            SettingsCard(title = stringResource(R.string.settings_section_privacy)) {
                                SwitchRow(
                                    title = stringResource(R.string.settings_posture_monitoring),
                                    subtitle = stringResource(R.string.settings_posture_monitoring_subtitle),
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

                            SettingsCard(title = stringResource(R.string.settings_section_debug)) {
                                ValueRow(
                                    title = stringResource(R.string.settings_entries),
                                    value = logs.size.toString(),
                                    valueColor = eInkTextColorOr(AccentOrange),
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                ActionRow(
                                    title = stringResource(R.string.settings_view_logs),
                                    hint = "→",
                                    onClick = { showLogViewer = true }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                ActionRow(
                                    title = stringResource(R.string.settings_clear_logs),
                                    hint = "×",
                                    titleColor = eInkTextColorOr(Color(0xFFB4371E)),
                                    onClick = { logs = emptyList() }
                                )
                            }

                            SettingsCard(title = stringResource(R.string.settings_section_info)) {
                                ValueRow(
                                    title = stringResource(R.string.settings_version),
                                    value = "1.0.0",
                                    valueColor = secondaryTextColor(),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        if (languageTransitionAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(appBackgroundColor().copy(alpha = languageTransitionAlpha))
            )
        }
    }

    if (showLogViewer) {
        LogViewerDialog(
            logs = logs,
            onDismiss = { showLogViewer = false }
        )
    }
    if (showLanguageDialog) {
        LanguagePickerDialog(
            selectedLanguage = appLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelect = {
                if (it != appLanguage) {
                    isApplyingLanguage = true
                    onApplyLanguage(it)
                    settingsViewModel.setAppLanguage(it)
                }
                showLanguageDialog = false
            }
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
                .background(appSurfaceColor(), RoundedCornerShape(Dimens.cardCorner))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    RoundedCornerShape(Dimens.cardCorner)
                )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = primaryTextColor(),
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
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = primaryTextColor()
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryTextColor()
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            modifier = Modifier.scale(0.8f),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentOrange,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
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
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = primaryTextColor(),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = secondaryTextColor(),
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
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = primaryTextColor(),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    hint: String,
    titleColor: Color = Color.Unspecified,
    onClick: () -> Unit,
) {
    val resolvedTitleColor = if (titleColor == Color.Unspecified) primaryTextColor() else titleColor
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
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = resolvedTitleColor,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = resolvedTitleColor,
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
                        text = pluralStringResource(R.plurals.settings_debug_logs_title, logs.size, logs.size),
                        style = MaterialTheme.typography.titleLarge.copy(color = YogurtSilk),
                    )
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.settings_close), color = eInkTextColorOr(AccentOrange), fontSize = 15.sp)
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
                                    color = eInkTextColorOr(log.level.color()),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                )
                                Text(
                                    text = "[${log.category}]",
                                    color = secondaryTextColor(),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = log.time,
                                    color = secondaryTextColor(),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Text(
                                text = log.message,
                                color = primaryTextColor(),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            log.data?.let {
                                Text(
                                    text = it,
                                    color = secondaryTextColor(),
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

@Composable
private fun currentLanguageLabel(language: AppLanguage): String {
    return when (language) {
        AppLanguage.SYSTEM -> stringResource(R.string.language_option_system)
        AppLanguage.ENGLISH -> stringResource(R.string.language_option_en)
        AppLanguage.CHINESE_SIMPLIFIED -> stringResource(R.string.language_option_zh_cn)
    }
}

@Composable
private fun LanguagePickerDialog(
    selectedLanguage: AppLanguage,
    onDismiss: () -> Unit,
    onSelect: (AppLanguage) -> Unit,
) {
    val options = listOf(
        AppLanguage.SYSTEM,
        AppLanguage.ENGLISH,
        AppLanguage.CHINESE_SIMPLIFIED
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = appSurfaceColor(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.language_dialog_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = primaryTextColor(),
                )
                options.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(language) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language == selectedLanguage,
                            onClick = { onSelect(language) },
                        )
                        Text(
                            text = currentLanguageLabel(language),
                            style = MaterialTheme.typography.bodyMedium,
                            color = primaryTextColor(),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = stringResource(R.string.settings_close),
                            color = eInkTextColorOr(AccentOrange),
                        )
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
