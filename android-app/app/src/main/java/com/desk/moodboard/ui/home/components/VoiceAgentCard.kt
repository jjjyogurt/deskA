package com.desk.moodboard.ui.home.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.desk.moodboard.ui.assistant.AssistantViewModel
import com.desk.moodboard.ui.assistant.ChatBubble
import com.desk.moodboard.ui.theme.*

@Composable
fun VoiceAgentCard(viewModel: AssistantViewModel) {
    val uiState by viewModel.uiState.collectAsState()
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

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
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
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChatBubbleOutline,
                        contentDescription = null,
                        tint = TextGrey,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "VOICE AGENT",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
                        color = TextGrey,
                        letterSpacing = 0.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Chat Area
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.messages) { message ->
                        ChatBubble(message)
                    }
                    if (uiState.isLoading) {
                        item {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = AccentOrange,
                                trackColor = FillGrey.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                // Unified Clean Input Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = FillGrey.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // THE SWIMMING FISH replacing the Mic button
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

                        Spacer(modifier = Modifier.width(4.dp))

                        // Clean Input Field
                        Box(modifier = Modifier.weight(1f)) {
                            if (inputText.isEmpty()) {
                                Text(
                                    "Ask me anything...", 
                                    color = TextGrey, 
                                    fontSize = 13.sp,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextDark,
                                    fontSize = 13.sp
                                ),
                                cursorBrush = SolidColor(AccentOrange),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Send Button (Right)
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
}

