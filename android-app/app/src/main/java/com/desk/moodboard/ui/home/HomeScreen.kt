package com.desk.moodboard.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desk.moodboard.ui.theme.AccentOrange
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.FillGrey
import com.desk.moodboard.ui.theme.SurfaceWhite
import com.desk.moodboard.ui.theme.TextDark
import com.desk.moodboard.ui.theme.TextGrey

@Composable
fun HomeScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent,
    ) {
        val scroll = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 24.dp, vertical = 16.dp), // More minimal padding
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left Column
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                MoodboardCard()
                HealthScoreCard()
            }

            // Right Column
            Column(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight()
            ) {
                CalendarCard()
            }
        }
    }
}

@Composable
private fun MoodboardCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Moodboard",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextDark
                )
                Text(
                    text = "0/140",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGrey
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(FillGrey.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "How are you feeling today?",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGrey.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.HealthScoreCard() {
    Card(
        modifier = Modifier.fillMaxWidth().weight(1f),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Health Score",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextDark
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Score Gauge
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(110.dp)) {
                        drawArc(
                            color = FillGrey.copy(alpha = 0.6f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "0",
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
                            color = TextDark
                        )
                        Text(
                            text = "SCORE",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontSize = 9.sp),
                            color = TextGrey
                        )
                    }
                }
                
                // Status Pill
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "STATUS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp, fontSize = 9.sp),
                        color = TextGrey,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Box(
                        modifier = Modifier
                            .background(FillGrey.copy(alpha = 0.5f), CircleShape)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(TextGrey, CircleShape))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Idle",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
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

@Composable
private fun CalendarCard() {
    Card(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "December 2025",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextDark
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CalendarNavButton("<")
                    CalendarNavButton(">")
                }
            }
            
            // Calendar Grid
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Days Header
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                        Text(
                            text = day,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = TextGrey
                        )
                    }
                }
                
                val decRows = listOf(
                    listOf(null, 1, 2, 3, 4, 5, 6),
                    listOf(7, 8, 9, 10, 11, 12, 13),
                    listOf(14, 15, 16, 17, 18, 19, 20),
                    listOf(21, 22, 23, 24, 25, 26, 27),
                    listOf(28, 29, 30, 31, null, null, null)
                )
                
                decRows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { day ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (day != null) {
                                    val isSelected = day == 24
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp, 32.dp)
                                                .background(AccentOrange, RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$day",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "$day",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                                            color = TextDark.copy(alpha = 0.9f)
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
}

@Composable
private fun CalendarNavButton(label: String) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(FillGrey.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = TextDark
        )
    }
}
