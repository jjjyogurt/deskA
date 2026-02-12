package com.desk.moodboard.ui.home.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desk.moodboard.data.model.NoteItem
import com.desk.moodboard.ui.home.TodoViewModel
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.appSurfaceColor
import com.desk.moodboard.ui.theme.eInkTextColorOr
import com.desk.moodboard.ui.theme.primaryTextColor
import com.desk.moodboard.ui.theme.secondaryTextColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesCard(viewModel: TodoViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    // Cleaner date + time formatter
    val dateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ENGLISH)
    var expandedNoteId by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(appSurfaceColor(), RoundedCornerShape(Dimens.cardCorner))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(Dimens.cardCorner)
                )
        ) {
            Column(
                modifier = Modifier.padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Idea Notes",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = primaryTextColor()
                )

                if (uiState.notes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Say to Voice Agent: “Note: My new idea”",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = secondaryTextColor()
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = uiState.notes,
                            key = { it.id }
                        ) { note ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.onDeleteNote(note)
                                        true
                                    } else false
                                },
                                positionalThreshold = { totalDistance -> totalDistance * 0.5f }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    val color = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                        Color.Red.copy(alpha = 0.8f)
                                    } else Color.Transparent

                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(color, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = eInkTextColorOr(Color.White)
                                        )
                                    }
                                }
                            ) {
                                NoteRow(
                                    note = note,
                                    formatter = dateTimeFormatter,
                                    isExpanded = expandedNoteId == note.id,
                                    onToggleExpand = {
                                        expandedNoteId = if (expandedNoteId == note.id) null else note.id
                                    }
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
private fun NoteRow(
    note: NoteItem,
    formatter: DateTimeFormatter,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit
) {
    val dateTimeText = Instant.ofEpochMilli(note.createdAt)
        .atZone(ZoneId.systemDefault())
        .format(formatter)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(appSurfaceColor()) // Ensure background is white for swipe
            .clickable { onToggleExpand() }
            .animateContentSize()
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        Text(
            text = note.title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            ),
            color = primaryTextColor(),
            maxLines = if (isExpanded) Int.MAX_VALUE else 1
        )

        if (isExpanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = note.content,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                ),
                color = secondaryTextColor()
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dateTimeText,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = secondaryTextColor()
            )
            if (!note.language.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = note.language,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = secondaryTextColor()
                )
            }
        }
    }
}
