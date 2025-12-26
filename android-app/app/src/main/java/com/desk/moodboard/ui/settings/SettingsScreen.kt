package com.desk.moodboard.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
fun SettingsScreen() {
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
                text = "Settings",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = YogurtNavy,
            )
            SettingsCard(title = "Reminders") {
                SettingRow(title = "Posture nudges", subtitle = "Gentle prompts every 30-45 min")
                HorizontalDivider(color = YogurtSilver.copy(alpha = 0.5f))
                SettingRow(title = "Standing reminders", subtitle = "Recommend 2-3 per day")
                HorizontalDivider(color = YogurtSilver.copy(alpha = 0.5f))
                SettingRow(title = "Hydration", subtitle = "Alerts when intake is low")
            }

            SettingsCard(title = "Vision Permissions") {
                SettingRow(title = "Camera posture detection", subtitle = "Required for scores")
                HorizontalDivider(color = YogurtSilver.copy(alpha = 0.5f))
                SettingRow(title = "Focus detection", subtitle = "Uses face mesh for focus")
            }

            SettingsCard(title = "Integrations") {
                SettingRow(title = "Calendar sync", subtitle = "Google / Microsoft / Lark")
                HorizontalDivider(color = YogurtSilver.copy(alpha = 0.5f))
                SettingRow(title = "Wearables", subtitle = "Apple Watch / Fitbit (beta)")
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
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
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = YogurtNavy)
            content()
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String) {
    val checked = remember { mutableStateOf(true) }
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = YogurtNavy)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = YogurtGrey)
        }
        Switch(
            checked = checked.value,
            onCheckedChange = { checked.value = it },
            colors = SwitchDefaults.colors(
                checkedThumbColor = YogurtSilk,
                checkedTrackColor = YogurtMint,
                uncheckedThumbColor = YogurtSilver,
                uncheckedTrackColor = YogurtSilver.copy(alpha = 0.3f)
            )
        )
    }
}
