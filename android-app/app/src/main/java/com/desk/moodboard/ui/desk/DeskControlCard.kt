package com.desk.moodboard.ui.desk

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.runtime.collectAsState
import android.util.Log
import com.desk.moodboard.domain.desk.DeskCommand
import com.desk.moodboard.domain.desk.DeskConnectionState
import com.desk.moodboard.domain.desk.DeskError
import com.desk.moodboard.domain.desk.DeskMemorySlot
import com.desk.moodboard.ui.theme.AccentOrange
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.FillGrey
import com.desk.moodboard.ui.theme.TextDark
import com.desk.moodboard.ui.theme.TextGrey
import kotlinx.coroutines.delay

@Composable
fun DeskControlCard(viewModel: DeskControlViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showDevicePicker by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var hasEverSelected by remember { mutableStateOf(false) }
    var showSelectionHighlight by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val hasScanPermission = hasScanPermission(results, context)
        val hasConnectPermission = hasConnectPermission(results, context)
        val hasLocationPermission = hasLocationPermission(results, context)
        viewModel.updatePermissions(hasScanPermission, hasConnectPermission, hasLocationPermission)
        if (hasScanPermission && hasConnectPermission && hasLocationPermission) {
            viewModel.startScan()
            showDevicePicker = true
        } else {
            showPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.updatePermissions(
            hasScanPermission = hasScanPermission(emptyMap(), context),
            hasConnectPermission = hasConnectPermission(emptyMap(), context),
            hasLocationPermission = hasLocationPermission(emptyMap(), context),
        )
    }

    LaunchedEffect(Unit) {
        val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
        val enabled = bluetoothManager?.adapter?.isEnabled == true
        viewModel.updateBluetoothEnabled(enabled)
    }

    LaunchedEffect(uiState.selectedDevice?.address) {
        val selectedAddress = uiState.selectedDevice?.address
        if (!hasEverSelected && selectedAddress != null) {
            hasEverSelected = true
            showSelectionHighlight = true
            delay(1200)
            showSelectionHighlight = false
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(Dimens.cardCorner))
                .border(1.dp, FillGrey.copy(alpha = 0.6f), RoundedCornerShape(Dimens.cardCorner))
                .padding(Dimens.cardPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val isConnected = uiState.connectionState is DeskConnectionState.Connected
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Desk Control",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextDark,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(
                        text = connectionLabel(uiState.connectionState),
                        onClick = if (isConnected) {
                            { showDisconnectDialog = true }
                        } else {
                            null
                        },
                    )
                }
            }

            if (!isConnected) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        Log.d("DeskControlCard", "Select Device clicked")
                        showDevicePicker = true
                        if (uiState.hasScanPermission && uiState.hasConnectPermission && uiState.hasLocationPermission) {
                            Log.d("DeskControlCard", "Permissions OK -> startScan")
                            viewModel.startScan()
                        } else {
                            Log.d("DeskControlCard", "Permissions missing -> request")
                            permissionLauncher.launch(requiredPermissions())
                            showPermissionDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = if (uiState.isScanning) "Scanning..." else "Select Device",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp),
                    )
                }
            }

            uiState.selectedDevice?.let { device ->
                val highlightModifier = if (showSelectionHighlight) {
                    Modifier
                        .background(AccentOrange.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                } else {
                    Modifier
                }
                Box(modifier = highlightModifier) {
                    Text(
                        text = "Selected: ${device.name ?: device.address}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGrey,
                    )
                }
            }

            val currentError = uiState.error
            if (currentError != null) {
                Text(
                    text = errorMessage(currentError),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGrey,
                )
            }

            Text(
                text = "Hold to move",
                style = MaterialTheme.typography.labelSmall,
                color = TextGrey,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HoldCommandButton(
                    label = "Up",
                    enabled = isConnected,
                    onPress = { viewModel.startContinuousCommand(DeskCommand.Up) },
                    onRelease = { viewModel.stopContinuousCommand() },
                )
                HoldCommandButton(
                    label = "Down",
                    enabled = isConnected,
                    onPress = { viewModel.startContinuousCommand(DeskCommand.Down) },
                    onRelease = { viewModel.stopContinuousCommand() },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CommandButton("Memory 1", enabled = isConnected) {
                    viewModel.sendCommand(DeskCommand.Memory(DeskMemorySlot.One))
                }
                CommandButton("Memory 2", enabled = isConnected) {
                    viewModel.sendCommand(DeskCommand.Memory(DeskMemorySlot.Two))
                }
                CommandButton("Memory 3", enabled = isConnected) {
                    viewModel.sendCommand(DeskCommand.Memory(DeskMemorySlot.Three))
                }
            }
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Desk", color = TextDark) },
            text = {
                Text(
                    "Are you sure you want to disconnect from the desk?",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGrey,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.disconnect()
                    showDisconnectDialog = false
                    showDevicePicker = true
                }) {
                    Text("Disconnect", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showDevicePicker) {
        DeskDevicePicker(
            devices = uiState.devices,
            isScanning = uiState.isScanning,
            selectedDeviceAddress = uiState.selectedDevice?.address,
            onDismiss = { showDevicePicker = false },
            onSelectDevice = { device ->
                viewModel.selectDevice(device)
                viewModel.connectSelectedDevice()
                showDevicePicker = false
            },
            onRescan = {
                if (uiState.hasScanPermission && uiState.hasConnectPermission && uiState.hasLocationPermission) {
                    viewModel.startScan()
                } else {
                    permissionLauncher.launch(requiredPermissions())
                    showPermissionDialog = true
                }
            },
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permissions required", color = TextDark) },
            text = {
                Text(
                    "Please enable Location and Nearby devices permissions in Settings to scan for desks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGrey,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    onClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .background(FillGrey.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .then(clickableModifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
            color = TextGrey,
        )
    }
}

@Composable
private fun RowScope.CommandButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier.weight(1f),
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RowScope.HoldCommandButton(
    label: String,
    enabled: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var wasPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed, enabled) {
        if (!enabled) {
            return@LaunchedEffect
        }
        if (isPressed == wasPressed) {
            return@LaunchedEffect
        }
        wasPressed = isPressed
        if (isPressed) {
            Log.d("DeskControlCard", "HoldCommandButton pressed: $label")
            onPress()
        } else {
            Log.d("DeskControlCard", "HoldCommandButton released: $label")
            onRelease()
        }
    }

    Button(
        modifier = Modifier.weight(1f),
        interactionSource = interactionSource,
        onClick = {},
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun connectionLabel(state: DeskConnectionState): String {
    return when (state) {
        DeskConnectionState.Disconnected -> "Disconnected"
        DeskConnectionState.Scanning -> "Scanning"
        DeskConnectionState.Connecting -> "Connecting"
        is DeskConnectionState.Connected -> "Connected"
        is DeskConnectionState.Error -> "Error"
    }
}

private fun errorMessage(error: DeskError): String {
    return when (error) {
        DeskError.PermissionDenied -> "Permission required for BLE scan."
        DeskError.BluetoothDisabled -> "Bluetooth is disabled."
        DeskError.DeviceNotFound -> "No device selected."
        DeskError.ConfigInvalid -> "Invalid BLE config."
        is DeskError.GattError -> "BLE error: ${error.message}"
        is DeskError.Unknown -> error.message
    }
}

private fun requiredPermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
}

private fun hasScanPermission(
    results: Map<String, Boolean>,
    context: android.content.Context,
): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        results[Manifest.permission.BLUETOOTH_SCAN] ?: hasPermission(context, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        hasPermission(context, Manifest.permission.BLUETOOTH)
    }
}

private fun hasConnectPermission(
    results: Map<String, Boolean>,
    context: android.content.Context,
): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        results[Manifest.permission.BLUETOOTH_CONNECT] ?: hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        hasPermission(context, Manifest.permission.BLUETOOTH_ADMIN)
    }
}

private fun hasLocationPermission(
    results: Map<String, Boolean>,
    context: android.content.Context,
): Boolean {
    return results[Manifest.permission.ACCESS_FINE_LOCATION]
        ?: hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
}

private fun hasPermission(context: android.content.Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
