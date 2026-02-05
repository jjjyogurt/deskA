package com.desk.moodboard.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.desk.moodboard.data.ble.DeskBleDevice
import com.desk.moodboard.ui.theme.AccentOrange
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.FillGrey
import com.desk.moodboard.ui.theme.TextDark
import com.desk.moodboard.ui.theme.TextGrey

@Composable
fun DeskDevicePicker(
    devices: List<DeskBleDevice>,
    selectedDeviceAddress: String?,
    onDismiss: () -> Unit,
    onSelectDevice: (DeskBleDevice) -> Unit,
    onRescan: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text("Select Desk", color = TextDark) },
        text = {
            val scrollState = rememberScrollState()
            BoxWithConstraints {
                val containerHeightPx = with(LocalDensity.current) { maxHeight.toPx() }
                val maxScroll = scrollState.maxValue.toFloat().coerceAtLeast(1f)
                val scrollFraction = (scrollState.value / maxScroll).coerceIn(0f, 1f)

                Box {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (devices.isEmpty()) {
                            Text(
                                text = "No devices found. Try rescanning.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextGrey,
                            )
                            TextButton(onClick = onRescan) {
                                Text("Rescan")
                            }
                        } else {
                            devices.forEach { device ->
                                DeviceRow(
                                    device = device,
                                    isSelected = device.address == selectedDeviceAddress,
                                    onClick = { onSelectDevice(device) }
                                )
                            }
                        }
                    }

                    val indicatorHeight = (containerHeightPx * 0.25f).coerceAtLeast(16f)
                    val indicatorOffset = (containerHeightPx - indicatorHeight) * scrollFraction

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxHeight()
                            .width(4.dp)
                            .background(FillGrey.copy(alpha = 0.25f))
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(y = with(LocalDensity.current) { indicatorOffset.toDp() })
                            .width(4.dp)
                            .height(with(LocalDensity.current) { indicatorHeight.toDp() })
                            .background(TextGrey.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                    )
                }
            }
        },
    )
}

@Composable
private fun DeviceRow(
    device: DeskBleDevice,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val baseColor = if (isSelected) AccentOrange.copy(alpha = 0.05f) else Color.White
    val containerColor = if (isPressed) FillGrey.copy(alpha = 0.18f) else baseColor
    val borderColor = when {
        isPressed -> FillGrey.copy(alpha = 0.45f)
        isSelected -> AccentOrange.copy(alpha = 0.2f)
        else -> Color.Transparent
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(Dimens.cardCorner))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(Dimens.cardCorner),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.name ?: "Unknown Device",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isSelected) AccentOrange else TextDark,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${device.rssi} dBm",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGrey,
                )
            }
            Text(
                text = device.address,
                style = MaterialTheme.typography.labelSmall,
                color = TextGrey.copy(alpha = 0.7f),
            )
        }
    }
}
