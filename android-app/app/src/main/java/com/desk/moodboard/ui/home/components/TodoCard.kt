package com.desk.moodboard.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desk.moodboard.ui.home.TodoFilter
import com.desk.moodboard.ui.home.TodoViewModel
import com.desk.moodboard.ui.theme.*
import kotlinx.datetime.*

@Composable
fun TodoCard(viewModel: TodoViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val visibleTodos = when (uiState.selectedFilter) {
        TodoFilter.TODAY -> uiState.todos.filter { it.dueDate == today }
        TodoFilter.ALL -> uiState.todos
        TodoFilter.COMPLETED -> uiState.todos.filter { it.isDone }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
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
                modifier = Modifier.padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Todo",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextDark
                    )
                }

                uiState.statusMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextGrey
                    )
                }

                if (visibleTodos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Say to Voice Agent: “Add groceries”",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = TextGrey
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(visibleTodos) { todo ->
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(14.dp)
                                        .background(
                                            if (todo.isDone) AccentOrange else Color.Transparent,
                                            CircleShape
                                        )
                                        .border(1.dp, if (todo.isDone) AccentOrange else TextGrey, CircleShape)
                                        .clickable { viewModel.onToggleTodoDone(todo) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (todo.isDone) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Completed",
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = todo.title,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp,
                                            textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        color = if (todo.isDone) TextGrey else TextDark,
                                        maxLines = 1
                                    )
                                    val dueLabel = buildString {
                                        if (todo.dueDate != null) append(todo.dueDate.toString())
                                        if (todo.dueTime != null) {
                                            if (isNotEmpty()) append(" ")
                                            append(todo.dueTime.toString())
                                        }
                                    }
                                    if (dueLabel.isNotBlank()) {
                                        Text(
                                            text = dueLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None
                                            ),
                                            color = TextGrey
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 44.dp, end = Dimens.cardPadding),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChipSmall(
                    selected = uiState.selectedFilter == TodoFilter.TODAY,
                    onClick = { viewModel.onSelectFilter(TodoFilter.TODAY) },
                    label = "Today"
                )
                FilterChipSmall(
                    selected = uiState.selectedFilter == TodoFilter.ALL,
                    onClick = { viewModel.onSelectFilter(TodoFilter.ALL) },
                    label = "All"
                )
                FilterChipSmall(
                    selected = uiState.selectedFilter == TodoFilter.COMPLETED,
                    onClick = { viewModel.onSelectFilter(TodoFilter.COMPLETED) },
                    label = "Done"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChipSmall(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 9.sp) },
        modifier = Modifier.height(24.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AccentOrange.copy(alpha = 0.15f),
            selectedLabelColor = AccentOrange,
            containerColor = FillGrey.copy(alpha = 0.2f),
            labelColor = TextGrey
        ),
        border = BorderStroke(1.dp, if (selected) AccentOrange.copy(alpha = 0.5f) else FillGrey.copy(alpha = 0.4f))
    )
}



