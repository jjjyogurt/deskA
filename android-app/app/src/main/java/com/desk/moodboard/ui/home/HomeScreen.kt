package com.desk.moodboard.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import com.desk.moodboard.ui.theme.*
import com.desk.moodboard.ui.assistant.AssistantViewModel
import com.desk.moodboard.ui.assistant.ChatBubble
import com.desk.moodboard.ui.home.TodoViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.datetime.toJavaLocalDate

@Composable
fun HomeScreen(
    assistantViewModel: AssistantViewModel = koinViewModel(),
    todoViewModel: TodoViewModel = koinViewModel(),
    calendarViewModel: CalendarViewModel = koinInject()
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundGrey,
    ) {
        val scroll = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = Dimens.screenPadding, vertical = 12.dp)
        ) {
            // ... header code ...
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val currentDateTime = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofPattern("E, MMM d h:mm a", Locale.ENGLISH)
                Text(
                    text = currentDateTime.format(formatter),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    color = TextGrey
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing)
            ) {
                // Left Column
                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing)
                ) {
                    MoodboardCard()
                    TodoCard(todoViewModel)
                    HealthScoreCard()
                }

                // Right Column
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing)
                ) {
                    CalendarCard(calendarViewModel)
                    AssistantCard(assistantViewModel)
                }
            }
        }
    }
}

@Composable
private fun TodoCard(viewModel: TodoViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val infiniteTransition = rememberInfiniteTransition(label = "todo-breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "todo-scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "todo-alpha"
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onToggleRecording(context)
        }
    }

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
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (uiState.isRecording) AccentOrange.copy(alpha = 0.15f) else FillGrey.copy(alpha = 0.4f),
                                CircleShape
                            )
                            .graphicsLayer(
                                scaleX = if (uiState.isRecording) scale else 1f,
                                scaleY = if (uiState.isRecording) scale else 1f,
                                alpha = if (uiState.isRecording) alpha else 1f
                            )
                            .clickable {
                                when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                    PackageManager.PERMISSION_GRANTED -> viewModel.onToggleRecording(context)
                                    else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Record",
                            tint = if (uiState.isRecording) AccentOrange else TextGrey,
                            modifier = Modifier.size(16.dp)
                        )
                    }
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
                            text = "Say: “Add buy groceries tomorrow 6pm”",
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
                FilterChip(
                    selected = uiState.selectedFilter == TodoFilter.TODAY,
                    onClick = { viewModel.onSelectFilter(TodoFilter.TODAY) },
                    label = { Text("Today", fontSize = 9.sp) },
                    modifier = Modifier.height(24.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentOrange.copy(alpha = 0.15f),
                        selectedLabelColor = AccentOrange,
                        containerColor = FillGrey.copy(alpha = 0.2f),
                        labelColor = TextGrey
                    ),
                    border = BorderStroke(1.dp, FillGrey.copy(alpha = 0.4f))
                )
                FilterChip(
                    selected = uiState.selectedFilter == TodoFilter.ALL,
                    onClick = { viewModel.onSelectFilter(TodoFilter.ALL) },
                    label = { Text("All", fontSize = 9.sp) },
                    modifier = Modifier.height(24.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentOrange.copy(alpha = 0.15f),
                        selectedLabelColor = AccentOrange,
                        containerColor = FillGrey.copy(alpha = 0.2f),
                        labelColor = TextGrey
                    ),
                    border = BorderStroke(1.dp, FillGrey.copy(alpha = 0.4f))
                )
                FilterChip(
                    selected = uiState.selectedFilter == TodoFilter.COMPLETED,
                    onClick = { viewModel.onSelectFilter(TodoFilter.COMPLETED) },
                    label = { Text("Completed", fontSize = 9.sp) },
                    modifier = Modifier.height(24.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentOrange.copy(alpha = 0.15f),
                        selectedLabelColor = AccentOrange,
                        containerColor = FillGrey.copy(alpha = 0.2f),
                        labelColor = TextGrey
                    ),
                    border = BorderStroke(1.dp, FillGrey.copy(alpha = 0.4f))
                )
            }
        }
    }
}

@Composable
private fun AssistantCard(viewModel: AssistantViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Breathing Animation
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

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
                        text = "AI CALENDAR ASSISTANT",
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
                        .padding(vertical = 8.dp)
                        .height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    color = FillGrey.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mic Button (Left)
                        IconButton(
                            onClick = { 
                                when (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)) {
                                    PackageManager.PERMISSION_GRANTED -> viewModel.onToggleRecording(context)
                                    else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .graphicsLayer(
                                    scaleX = if (uiState.isRecording) scale else 1f,
                                    scaleY = if (uiState.isRecording) scale else 1f,
                                    alpha = if (uiState.isRecording) alpha else 1f
                                )
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Record",
                                tint = if (uiState.isRecording) AccentOrange else TextDark,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Clean Input Field
                        Box(modifier = Modifier.weight(1f)) {
                            if (inputText.isEmpty()) {
                                Text(
                                    "Ask me anything...", 
                                    color = TextGrey, 
                                    fontSize = 14.sp,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )
                            }
                            BasicTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = TextDark,
                                    fontSize = 14.sp
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

@Composable
private fun MoodboardCard() {
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
                        text = "0/140",
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
                    Text(
                        text = "How are you feeling today?",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = TextGrey.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.HealthScoreCard() {
    Card(
        modifier = Modifier.fillMaxWidth().weight(1f),
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
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Health Score",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextDark
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Score Gauge
                    Box(
                        modifier = Modifier.size(90.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(90.dp)) {
                            drawArc(
                                color = FillGrey.copy(alpha = 0.6f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "0",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
                                color = TextDark
                            )
                            Text(
                                text = "SCORE",
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontSize = 10.sp),
                                color = TextGrey
                            )
                        }
                    }
                    
                    // Status Pill
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "STATUS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp, fontSize = 10.sp),
                            color = TextGrey,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Box(
                            modifier = Modifier
                                .background(FillGrey.copy(alpha = 0.5f), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(5.dp).background(TextGrey, CircleShape))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Idle",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                                    color = TextDark
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
            }
        }
    }
}

@Composable
private fun CalendarCard(viewModel: CalendarViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    
    Card(
        modifier = Modifier.fillMaxWidth().height(300.dp),
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
                // Header with Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val headerText = when (uiState.viewMode) {
                            CalendarViewMode.MONTH -> "${uiState.selectedDate.month} ${uiState.selectedDate.year}"
                            CalendarViewMode.WEEK -> "Week of ${uiState.selectedDate.dayOfMonth} ${uiState.selectedDate.month}"
                            CalendarViewMode.DAY -> "${uiState.selectedDate.dayOfWeek}, ${uiState.selectedDate.month} ${uiState.selectedDate.dayOfMonth}"
                        }
                    Text(
                            text = headerText,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextDark
                    )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        CalendarNavButton("<") { viewModel.previous() }
                        CalendarNavButton(">") { viewModel.next() }
                    }
                }

                // View Mode Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ViewModeButton("Day", uiState.viewMode == CalendarViewMode.DAY) {
                        viewModel.setViewMode(CalendarViewMode.DAY)
                    }
                    ViewModeButton("Week", uiState.viewMode == CalendarViewMode.WEEK) {
                        viewModel.setViewMode(CalendarViewMode.WEEK)
                    }
                    ViewModeButton("Month", uiState.viewMode == CalendarViewMode.MONTH) {
                        viewModel.setViewMode(CalendarViewMode.MONTH)
                    }
                }
                
                // Dynamic View Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (uiState.viewMode) {
                        CalendarViewMode.MONTH -> MonthView(uiState.selectedDate, uiState.events, viewModel)
                        CalendarViewMode.WEEK -> WeekView(uiState.selectedDate, uiState.events)
                        CalendarViewMode.DAY -> DayView(uiState.selectedDate, uiState.events)
                    }
                }

                // Selected Day Events
                if (uiState.viewMode == CalendarViewMode.MONTH) {
                    val selectedDayEvents = uiState.events.filter { it.startTime.date == uiState.selectedDate }
                    if (selectedDayEvents.isNotEmpty()) {
                        Divider(color = FillGrey.copy(alpha = 0.3f))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            selectedDayEvents.take(2).forEach { event ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(AccentOrange, CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = event.title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextDark,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(4.dp),
        color = if (isSelected) AccentOrange.copy(alpha = 0.1f) else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, AccentOrange) else null
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) AccentOrange else TextGrey,
                fontSize = 9.sp
            )
        )
    }
}

@Composable
private fun MonthView(
    selectedDate: LocalDate,
    events: List<com.desk.moodboard.data.model.CalendarEvent>,
    viewModel: CalendarViewModel
) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Days Header
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextGrey,
                                fontSize = 10.sp
                            )
                        }
                    }
                    
        val firstDayOfMonth = LocalDate(selectedDate.year, selectedDate.month, 1)
        val daysInMonth = selectedDate.toJavaLocalDate().lengthOfMonth()
        val firstDayOfWeek = firstDayOfMonth.dayOfWeek.ordinal % 7 // Adjusted for Sunday start

        val days = mutableListOf<Int?>()
        repeat(firstDayOfWeek + 1) { days.add(null) } // Adjusted for 0-based ordinal
        for (i in 1..daysInMonth) { days.add(i) }
        while (days.size % 7 != 0) { days.add(null) }

        days.chunked(7).forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { day ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                            .height(24.dp)
                            .clickable(enabled = day != null) {
                                day?.let { viewModel.selectDate(LocalDate(selectedDate.year, selectedDate.month, it)) }
                            },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (day != null) {
                            val isSelected = day == selectedDate.dayOfMonth
                            val dateAtDay = LocalDate(selectedDate.year, selectedDate.month, day)
                            val hasEvent = events.any { it.startTime.date == dateAtDay }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                            .size(24.dp, 18.dp)
                                                    .background(AccentOrange, RoundedCornerShape(4.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$day",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 10.sp)
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "$day",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                                color = TextDark.copy(alpha = 0.9f),
                                                fontSize = 10.sp
                                            )
                                        }
                                if (hasEvent && !isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 1.dp)
                                            .size(3.dp)
                                            .background(AccentOrange, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekView(
    selectedDate: LocalDate,
    events: List<com.desk.moodboard.data.model.CalendarEvent>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Simple list of days in the week
        val firstDayOfWeek = selectedDate.minus(selectedDate.dayOfWeek.ordinal.toLong(), DateTimeUnit.DAY)

        (0..6).forEach { i ->
            val day = firstDayOfWeek.plus(i.toLong(), DateTimeUnit.DAY)
            val dayEvents = events.filter { it.startTime.date == day }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (day == selectedDate) AccentOrange.copy(alpha = 0.05f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = day.dayOfWeek.name.take(3),
                    modifier = Modifier.width(30.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (day == selectedDate) AccentOrange else TextGrey,
                    fontSize = 10.sp
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    modifier = Modifier.width(20.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextDark,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (dayEvents.isNotEmpty()) {
                    Text(
                        text = dayEvents.first().title,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGrey,
                        fontSize = 9.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(FillGrey.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

@Composable
private fun DayView(
    selectedDate: LocalDate,
    events: List<com.desk.moodboard.data.model.CalendarEvent>
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Time slots
        (8..20).forEach { hour ->
            val hourEvents = events.filter { it.startTime.hour == hour }
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("%02d:00", hour),
                    modifier = Modifier.width(40.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGrey,
                    fontSize = 10.sp
                )
                if (hourEvents.isNotEmpty()) {
                    Card(
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = AccentOrange.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(start = 8.dp)) {
                            Text(
                                text = hourEvents.first().title,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = AccentOrange,
                                fontSize = 9.sp
                            )
                        }
                    }
                } else {
    Box(
        modifier = Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(FillGrey.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarNavButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(24.dp),
        shape = RoundedCornerShape(4.dp),
        color = FillGrey.copy(alpha = 0.4f)
    ) {
        Box(contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = TextDark,
            fontSize = 11.sp
        )
        }
    }
}