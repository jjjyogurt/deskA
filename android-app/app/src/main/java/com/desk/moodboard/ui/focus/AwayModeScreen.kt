package com.desk.moodboard.ui.focus

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.desk.moodboard.R
import com.desk.moodboard.ui.theme.AccentOrange
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.YogurtMint
import com.desk.moodboard.ui.theme.appBackgroundColor
import com.desk.moodboard.ui.theme.appSurfaceColor
import com.desk.moodboard.ui.theme.eInkTextColorOr
import com.desk.moodboard.ui.theme.primaryTextColor
import com.desk.moodboard.ui.theme.secondaryTextColor

@Composable
fun AwayModeScreen(
    uiState: AwayModeUiState,
    onBack: () -> Unit,
    onOpenGreetingEditor: () -> Unit,
    onUpdateGreetingDraft: (String) -> Unit,
    onRequestCloseGreetingEditor: () -> Unit,
    onDismissDiscardDialog: () -> Unit,
    onDiscardGreetingChanges: () -> Unit,
    onSaveGreetingDraft: () -> Unit,
    onToggleRecording: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { /* Prevent dismissing by tapping outside to maintain kiosk mode */ },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = appBackgroundColor()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.screenPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        onClick = { onBack() },
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape,
                        color = appSurfaceColor()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = stringResource(R.string.away_exit),
                                tint = secondaryTextColor(),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(0.12f))

                // Greeting Section
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onOpenGreetingEditor() }
                ) {
                    Text(
                        text = uiState.customGreeting,
                        style = MaterialTheme.typography.displayMedium,
                        color = primaryTextColor(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.away_tap_to_edit),
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryTextColor()
                    )
                }

                Spacer(modifier = Modifier.weight(0.4f))

                // Record Button & Success Feedback
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RecordMicButton(
                        isRecording = uiState.isRecording,
                        onClick = onToggleRecording
                    )

                    when (uiState.recordingStage) {
                        RecordingStage.Saving -> {
                            Text(
                                text = stringResource(R.string.away_saving_recording),
                                style = MaterialTheme.typography.labelMedium,
                                color = secondaryTextColor()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        RecordingStage.Transcribing -> {
                            Text(
                                text = stringResource(R.string.away_transcribing),
                                style = MaterialTheme.typography.labelMedium,
                                color = secondaryTextColor()
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        else -> Unit
                    }

                    uiState.errorMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentOrange
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (uiState.showSuccessFeedback) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            shape = CircleShape,
                            color = YogurtMint.copy(alpha = 0.2f),
                            contentColor = YogurtMint
                        ) {
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = android.R.drawable.checkbox_on_background),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.away_saved_success),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    } else {
                        // Keep layout stable when feedback is hidden
                        Spacer(modifier = Modifier.height(16.dp + 32.dp))
                    }
                }

                Spacer(modifier = Modifier.weight(0.3f))

                // I'm Back Button
                if (!uiState.isRecording) {
                    TextButton(onClick = onBack) {
                        Text(
                            text = stringResource(R.string.away_back),
                            style = MaterialTheme.typography.labelLarge,
                            color = secondaryTextColor()
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(48.dp)) // Maintain spacing when button is hidden
                }
            }
        }

        if (uiState.isGreetingEditorOpen) {
            EditGreetingFullscreen(
                draftGreeting = uiState.greetingDraft,
                onDismiss = onRequestCloseGreetingEditor,
                onValueChange = onUpdateGreetingDraft,
                onSave = onSaveGreetingDraft
            )
        }

        if (uiState.showDiscardGreetingDialog) {
            AlertDialog(
                onDismissRequest = onDismissDiscardDialog,
                title = { Text(stringResource(R.string.away_discard_title)) },
                text = { Text(stringResource(R.string.away_discard_body)) },
                confirmButton = {
                    TextButton(onClick = onDiscardGreetingChanges) {
                        Text(stringResource(R.string.away_discard), color = AccentOrange)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissDiscardDialog) {
                        Text(stringResource(R.string.away_keep_editing), color = primaryTextColor())
                    }
                },
                containerColor = appSurfaceColor(),
                textContentColor = primaryTextColor(),
                titleContentColor = primaryTextColor()
            )
        }
    }
}

@Composable
fun RecordMicButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(120.dp)
    ) {
        // Pulsing background
        if (isRecording) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale)
                    .background(AccentOrange.copy(alpha = 0.2f), CircleShape)
            )
        }
        
        // Main button
        Surface(
            onClick = onClick,
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            color = if (isRecording) AccentOrange else YogurtMint,
            contentColor = eInkTextColorOr(Color.White)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = if (isRecording) android.R.drawable.ic_media_pause else android.R.drawable.ic_btn_speak_now),
                    contentDescription = if (isRecording) {
                        stringResource(R.string.away_stop_recording)
                    } else {
                        stringResource(R.string.away_start_recording)
                    },
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
    
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = if (isRecording) {
            stringResource(R.string.away_tap_to_stop_save)
        } else {
            stringResource(R.string.away_tap_to_record)
        },
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = secondaryTextColor()
    )
}

@Composable
fun EditGreetingFullscreen(
    draftGreeting: String,
    onDismiss: () -> Unit,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            color = appSurfaceColor()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.away_edit_greeting),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = primaryTextColor()
                    )
                    Surface(
                        onClick = { onDismiss() },
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape,
                        color = appBackgroundColor()
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_close_clear_cancel),
                                contentDescription = stringResource(R.string.content_desc_close),
                                tint = secondaryTextColor(),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = draftGreeting,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = appBackgroundColor(),
                        unfocusedContainerColor = appBackgroundColor(),
                        focusedIndicatorColor = MaterialTheme.colorScheme.outline,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    minLines = 6
                )

                TextButton(
                    onClick = onSave,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        text = stringResource(R.string.away_save),
                        color = primaryTextColor(),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}