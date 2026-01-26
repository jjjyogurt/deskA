package com.desk.moodboard.ui.home.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.moodboard.ui.assistant.AssistantViewModel
import com.desk.moodboard.ui.assistant.ChatBubble
import com.desk.moodboard.ui.theme.*

@Composable
fun FloatingVoiceAgent(viewModel: AssistantViewModel) {
    var isExpanded by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onToggleRecording(context)
        }
    }

    // Auto-scroll logic
    LaunchedEffect(uiState.messages.size, isExpanded) {
        if (uiState.messages.isNotEmpty() && isExpanded) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Animate height and width transitions
    val height by animateDpAsState(
        targetValue = if (isExpanded) 360.dp else 48.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "height"
    )
    val width by animateDpAsState(
        targetValue = if (isExpanded) 280.dp else 220.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "width"
    )

    Surface(
        modifier = Modifier
            .size(width = width, height = height),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, FillGrey.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (isExpanded) {
                // Header to collapse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable { isExpanded = false },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "VOICE AGENT",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
                        color = TextGrey,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = TextGrey,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Chat Area
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(uiState.messages) { message ->
                        ChatBubble(message)
                    }
                    if (uiState.isLoading) {
                        item {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = AccentOrange,
                                trackColor = FillGrey.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }

            // DYNAMIC STRIP TEXT LOGIC
            val stripDisplay = when {
                uiState.isRecording && uiState.currentTranscript.isNotEmpty() -> uiState.currentTranscript
                uiState.isRecording -> "Listening..."
                uiState.isLoading -> "Thinking..."
                uiState.messages.isNotEmpty() && !isExpanded -> uiState.messages.last().text
                else -> "Ask me anything..."
            }

            // Determine color for the strip text
            val stripTextColor = when {
                uiState.isRecording -> AccentOrange
                uiState.isLoading -> TextGrey.copy(alpha = 0.6f)
                uiState.messages.isNotEmpty() && !uiState.messages.last().isUser && !isExpanded -> AccentOrange.copy(alpha = 0.9f)
                else -> TextGrey
            }

            // The persistent Input Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Swimming Fish (Toggle Recording)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable {
                            when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                PackageManager.PERMISSION_GRANTED -> viewModel.onToggleRecording(context)
                                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    SwimmingFish(isRecording = uiState.isRecording)
                }

                // Text Input Box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = !isExpanded) { isExpanded = true }
                        .padding(horizontal = 4.dp)
                ) {
                    if (inputText.isEmpty()) {
                        key(stripDisplay) {
                            Text(
                                text = stripDisplay,
                                color = stripTextColor,
                                fontSize = 10.sp, // 20% smaller for Bauhaus minimalism
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .basicMarquee( // Auto-scroll for long status/responses, limit to 3 rounds per new text
                                        iterations = 3,
                                        repeatDelayMillis = 1500
                                    ),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (uiState.isRecording || uiState.isLoading) FontWeight.Medium else FontWeight.Normal
                                )
                            )
                        }
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = {
                            inputText = it
                            if (it.isNotEmpty() && !isExpanded) isExpanded = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterStart),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = TextDark,
                            fontSize = if (isExpanded) 13.sp else 10.sp
                        ),
                        cursorBrush = SolidColor(AccentOrange),
                        singleLine = true,
                        enabled = true
                    )
                }

                if (!isExpanded) {
                    IconButton(
                        onClick = { isExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Expand",
                            tint = TextGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    // Send Button
                    IconButton(
                        onClick = {
                            viewModel.onSendMessage(inputText)
                            inputText = ""
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (inputText.isNotBlank()) Color(0xFF333333) else Color.Transparent,
                                CircleShape
                            ),
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) Color.White else TextGrey.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
