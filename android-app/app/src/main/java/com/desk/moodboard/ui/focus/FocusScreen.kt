package com.desk.moodboard.ui.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.TaskAlt
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
fun FocusScreen() {
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
                text = "Focus Mode",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = YogurtNavy,
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
                ) {
                    TimerCard()
                    TasksCard()
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
                ) {
                    FocusQualityCard()
                    NotificationCard()
                }
            }
        }
    }
}

@Composable
private fun TimerCard() {
    Card(
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = YogurtSilk),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "Session Timer", style = MaterialTheme.typography.titleLarge, color = YogurtNavy)
            Text(
                text = "Active focus session • 32:18 remaining",
                style = MaterialTheme.typography.bodyMedium,
                color = YogurtGrey,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .background(YogurtSilver.copy(alpha = 0.3f), CircleShape),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.64f)
                        .fillMaxHeight()
                        .background(YogurtBlue, CircleShape),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlChip("Resume", YogurtMint, Icons.Outlined.PlayArrow)
                ControlChip("Break soon", Color(0xFFFFC27D), Icons.Outlined.Notifications)
            }
        }
    }
}

@Composable
private fun ControlChip(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun TasksCard() {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Tasks", style = MaterialTheme.typography.titleLarge, color = YogurtNavy)
                Text(text = "Today", style = MaterialTheme.typography.labelLarge, color = YogurtGrey)
            }
            TaskRow("Daily posture check-in", true)
            HorizontalDivider(color = YogurtSilver.copy(alpha = 0.5f))
            TaskRow("Write design update", false)
            HorizontalDivider(color = YogurtSilver.copy(alpha = 0.5f))
            TaskRow("Water reminder", true)
            HorizontalDivider(color = YogurtSilver.copy(alpha = 0.5f))
            TaskRow("Book physiotherapy", false)
        }
    }
}

@Composable
private fun TaskRow(title: String, done: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = YogurtNavy)
            Text(text = if (done) "Completed" else "Pending", style = MaterialTheme.typography.bodySmall, color = YogurtGrey)
        }
        Icon(
            imageVector = Icons.Outlined.TaskAlt,
            contentDescription = null,
            tint = if (done) YogurtMint else YogurtSilver,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun FocusQualityCard() {
    Card(
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = YogurtSilk),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = "Focus Quality", style = MaterialTheme.typography.titleLarge, color = YogurtNavy)
            Text(
                text = "Vision-based focus score from posture + eye tracking.",
                style = MaterialTheme.typography.bodyMedium,
                color = YogurtGrey,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FocusChip("No phone", YogurtMint, "12 min")
                FocusChip("Head up", YogurtBlue, "32 min")
            }
        }
    }
}

@Composable
private fun RowScope.FocusChip(title: String, color: Color, detail: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, color = color)
        Text(text = detail, style = MaterialTheme.typography.bodySmall, color = YogurtGrey)
    }
}

@Composable
private fun NotificationCard() {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Notifications", style = MaterialTheme.typography.titleLarge, color = YogurtNavy)
                Icon(imageVector = Icons.Outlined.Notifications, contentDescription = null, tint = Color(0xFFFFC27D))
            }
            Notice("Posture looks great. Keep neck aligned.")
            HorizontalDivider(color = YogurtSilver.copy(alpha = 0.5f))
            Notice("Break in 10 min. Stretch shoulders.")
            HorizontalDivider(color = YogurtSilver.copy(alpha = 0.5f))
            Notice("Water intake is low today.")
        }
    }
}

@Composable
private fun Notice(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = YogurtNavy, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .background(YogurtSilver.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(text = "View", style = MaterialTheme.typography.labelLarge, color = YogurtNavy)
        }
    }
}
