package com.desk.moodboard.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.desk.moodboard.ui.assistant.AssistantViewModel
import com.desk.moodboard.ui.theme.*
import java.util.Locale

@Composable
fun MoodboardCard(assistantViewModel: AssistantViewModel) {
    val uiState by assistantViewModel.uiState.collectAsStateWithLifecycle()
    var moodText by rememberSaveable { mutableStateOf("") }
    var lastProcessedMessageId by rememberSaveable { mutableStateOf<String?>(null) }
    val latestUserMessage = uiState.messages.lastOrNull { it.isUser }

    LaunchedEffect(latestUserMessage?.id) {
        val message = latestUserMessage ?: return@LaunchedEffect
        if (message.id == lastProcessedMessageId) {
            return@LaunchedEffect
        }
        val extracted = extractMoodText(message.text)
        if (!extracted.isNullOrBlank()) {
            moodText = extracted.take(MoodboardMaxLength)
            lastProcessedMessageId = message.id
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = null
    ) {
        Box(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(Dimens.cardCorner))
                .border(1.dp, FillGrey.copy(alpha = 0.6f), RoundedCornerShape(Dimens.cardCorner))
        ) {
            Column(
                modifier = Modifier.padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Moodboard",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextDark
                    )
                    Text(
                        text = "${moodText.length}/$MoodboardMaxLength",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextGrey
                    )
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(FillGrey.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    if (moodText.isBlank()) {
                        Text(
                            text = "How are you feeling today?",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                            color = TextGrey.copy(alpha = 0.7f)
                        )
                    }
                    BasicTextField(
                        value = moodText,
                        onValueChange = { value ->
                            moodText = value.take(MoodboardMaxLength)
                        },
                        modifier = Modifier.fillMaxSize(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = TextDark
                        ),
                        maxLines = 3,
                        singleLine = false
                    )
                }
            }
        }
    }
}

private const val MoodboardMaxLength = 140
private const val MoodboardTrigger = "write down my mood"

private fun extractMoodText(input: String): String? {
    val trimmed = input.trim()
    val normalized = trimmed.lowercase(Locale.ENGLISH)
    if (!normalized.startsWith(MoodboardTrigger)) {
        return null
    }
    val remainder = trimmed.drop(MoodboardTrigger.length)
        .trim()
        .trimStart(':', '-', ' ')
        .trim()
    return if (remainder.isBlank()) null else remainder
}

