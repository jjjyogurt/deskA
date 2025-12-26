package com.desk.moodboard.ui.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.YogurtBlue
import com.desk.moodboard.ui.theme.YogurtGrey
import com.desk.moodboard.ui.theme.YogurtMint
import com.desk.moodboard.ui.theme.YogurtNavy
import com.desk.moodboard.ui.theme.YogurtSilver
import com.desk.moodboard.ui.theme.YogurtSilk

@Composable
fun HealthScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            Text(
                text = "Health Overview",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = YogurtNavy,
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.8f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
                ) {
                    PostureTrendCard()
                    HydrationCard()
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
                ) {
                    HeartRateCard()
                    ReminderCard()
                }
            }
        }
    }
}

@Composable
private fun PostureTrendCard() {
    Card(
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = YogurtSilk),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Posture Trend", style = MaterialTheme.typography.titleLarge, color = YogurtNavy)
                Icon(imageVector = Icons.Outlined.Timeline, contentDescription = null, tint = YogurtNavy.copy(alpha = 0.3f))
            }
            Text(
                text = "Weekly improvements shown as upright vs slouch time.",
                style = MaterialTheme.typography.bodyMedium,
                color = YogurtGrey,
            )
            BarChart(
                values = listOf(0.72f, 0.64f, 0.81f, 0.78f, 0.86f, 0.74f, 0.69f),
                labels = listOf("M", "T", "W", "T", "F", "S", "S"),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Legend(color = YogurtMint, text = "Upright")
                Legend(color = YogurtSilver, text = "Other")
            }
        }
    }
}

@Composable
private fun BarChart(values: List<Float>, labels: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        values.zip(labels).forEach { (value, label) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(160.dp)
                        .background(YogurtSilver.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(160.dp * value)
                            .background(YogurtMint, RoundedCornerShape(12.dp)),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = label, style = MaterialTheme.typography.labelMedium, color = YogurtGrey)
            }
        }
    }
}

@Composable
private fun HydrationCard() {
    Card(
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = YogurtSilk),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.cardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Hydration Accuracy", style = MaterialTheme.typography.titleLarge, color = YogurtNavy)
                Text(
                    text = "Tracking via scale + camera · target 2000 ml",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YogurtGrey,
                )
                Spacer(modifier = Modifier.height(4.dp))
                ProgressBar(fraction = 0.32f, color = YogurtBlue, height = 12.dp)
                Text(
                    text = "640 ml logged",
                    style = MaterialTheme.typography.labelLarge,
                    color = YogurtNavy,
                )
            }
            Spacer(modifier = Modifier.width(24.dp))
            Icon(imageVector = Icons.Outlined.LocalDrink, contentDescription = null, tint = YogurtBlue.copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun HeartRateCard() {
    Card(
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = YogurtSilk),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.cardPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Heart Rate (vision)", style = MaterialTheme.typography.titleLarge, color = YogurtNavy)
                Text(
                    text = "Optical-only estimation",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YogurtGrey,
                )
                Ring(value = 68, color = YogurtBlue, label = "BPM")
            }
            Icon(imageVector = Icons.Outlined.Favorite, contentDescription = null, tint = Color(0xFFF49B9B).copy(alpha = 0.4f), modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun ReminderCard() {
    Card(
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = YogurtSilk),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Reminders", style = MaterialTheme.typography.titleLarge, color = YogurtNavy)
            ReminderRow("Stretch", "every 45 min", YogurtBlue)
            HorizontalDivider(color = YogurtSilver.copy(alpha = 0.5f))
            ReminderRow("Water", "every 60 min", YogurtBlue)
            HorizontalDivider(color = YogurtSilver.copy(alpha = 0.5f))
            ReminderRow("Stand", "3 x per day", YogurtMint)
        }
    }
}

@Composable
private fun ReminderRow(title: String, cadence: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = YogurtNavy)
            Text(text = cadence, style = MaterialTheme.typography.bodySmall, color = YogurtGrey)
        }
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(text = "On", style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Composable
private fun Ring(value: Int, color: Color, label: String) {
    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(120.dp)) {
            drawArc(
                color = YogurtSilver.copy(alpha = 0.3f),
                startAngle = -210f,
                sweepAngle = 240f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -210f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$value", style = MaterialTheme.typography.displaySmall, color = YogurtNavy)
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = YogurtGrey)
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float, color: Color, height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(YogurtSilver.copy(alpha = 0.3f), CircleShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(color, CircleShape),
        )
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = YogurtGrey)
    }
}
