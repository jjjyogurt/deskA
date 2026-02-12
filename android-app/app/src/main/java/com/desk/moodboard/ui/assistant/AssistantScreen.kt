package com.desk.moodboard.ui.assistant

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.desk.moodboard.data.model.ChatMessage
import com.desk.moodboard.ui.theme.AccentOrange
import com.desk.moodboard.ui.theme.appBackgroundColor
import com.desk.moodboard.ui.theme.eInkTextColorOr
import com.desk.moodboard.ui.theme.primaryTextColor
import com.desk.moodboard.ui.theme.secondaryTextColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(viewModel: AssistantViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(appBackgroundColor())
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    tint = secondaryTextColor(),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "AI CALENDAR ASSISTANT",
                    style = MaterialTheme.typography.labelLarge,
                    color = secondaryTextColor(),
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Chat History
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                reverseLayout = false,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(uiState.messages) { message ->
                    ChatBubble(message)
                }
                if (uiState.isRecording && uiState.currentTranscript.isNotEmpty()) {
                    item {
                        ChatBubble(ChatMessage("preview", uiState.currentTranscript, true))
                    }
                }
                if (uiState.isLoading) {
                    item {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(8.dp),
                            strokeWidth = 2.dp,
                            color = AccentOrange
                        )
                    }
                }
            }

            // Input Area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = appBackgroundColor(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask me to schedule something...", color = secondaryTextColor()) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            cursorColor = AccentOrange
                        ),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            viewModel.onSendMessage(inputText)
                            inputText = ""
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color(0xFF333333), RoundedCornerShape(8.dp)),
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = eInkTextColorOr(Color.White),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.onToggleRecording(context) },
                        modifier = Modifier
                            .size(34.dp)
                            .background(if (uiState.isRecording) AccentOrange else Color(0xFFF1F3F4), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Record",
                            tint = if (uiState.isRecording) eInkTextColorOr(Color.White) else primaryTextColor(),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Success Animation Overlay
        AnimatedVisibility(
            visible = uiState.showSuccessCheck,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 1.2f),
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(1f)
        ) {
            SuccessCheckmark()
        }
    }
}

@Composable
fun SuccessCheckmark() {
    Box(
        modifier = Modifier
            .size(110.dp)
            .shadow(elevation = 8.dp, shape = CircleShape)
            .background(color = Color.White.copy(alpha = 0.95f), shape = CircleShape)
            .clip(CircleShape)
            .border(width = 0.5.dp, color = Color.LightGray.copy(alpha = 0.3f), shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Success",
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(56.dp)
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val alignment = if (message.isUser) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isUser) AccentOrange.copy(alpha = 0.1f) else Color(0xFFE3F2FD)
    val textColor = primaryTextColor()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (message.isUser) 12.dp else 0.dp,
                bottomEnd = if (message.isUser) 0.dp else 12.dp
            ),
            border = if (message.isUser) null else null // Border can be added if needed
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(12.dp),
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = "Just now", // Placeholder for timestamp formatting
            style = MaterialTheme.typography.labelSmall,
            color = secondaryTextColor(),
            modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
        )
    }
}




