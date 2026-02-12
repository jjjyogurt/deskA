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
import androidx.compose.ui.text.style.TextOverflow
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
import com.desk.moodboard.ui.theme.appSurfaceColor
import com.desk.moodboard.ui.theme.eInkTextColorOr
import com.desk.moodboard.ui.theme.primaryTextColor
import com.desk.moodboard.ui.theme.secondaryTextColor

@Composable
fun DeskControlCard(viewModel: DeskControlViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showDeskDevicePicker by remember { mutableStateOf(false) }
    var showRemoteDevicePicker by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showDeskDisconnectDialog by remember { mutableStateOf(false) }
    var showRemoteDisconnectDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val hasScanPermission = hasScanPermission(results, context)
        val hasConnectPermission = hasConnectPermission(results, context)
        val hasLocationPermission = hasLocationPermission(results, context)
        viewModel.updatePermissions(
            hasScanPermission = hasScanPermission,
            hasConnectPermission = hasConnectPermission,
            hasLocationPermission = hasLocationPermission,
        )
        if (!(hasScanPermission && hasConnectPermission && hasLocationPermission)) {
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .background(appSurfaceColor(), RoundedCornerShape(Dimens.cardCorner))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    RoundedCornerShape(Dimens.cardCorner)
                )
                .padding(horizontal = Dimens.cardPadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val isDeskConnected = uiState.connectionState is DeskConnectionState.Connected
            val isRemoteConnected = uiState.remoteConnectionState is DeskConnectionState.Connected
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Desk Control",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = primaryTextColor(),
                )

                StatusChip(
                    text = "Desk: ${connectionLabel(uiState.connectionState)}",
                    onClick = if (isDeskConnected) {
                        { showDeskDisconnectDialog = true }
                    } else {
                        null
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(
                    text = "Remote: ${connectionLabel(uiState.remoteConnectionState)}",
                    onClick = if (isRemoteConnected) {
                        { showRemoteDisconnectDialog = true }
                    } else {
                        null
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (uiState.hasScanPermission && uiState.hasConnectPermission && uiState.hasLocationPermission) {
                            showDeskDevicePicker = true
                            viewModel.startDeskScan()
                        } else {
                            permissionLauncher.launch(requiredPermissions())
                            showPermissionDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = if (uiState.isScanning) "Scanning Desk..." else "Connect Desk",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 9.sp),
                    )
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (uiState.hasScanPermission && uiState.hasConnectPermission && uiState.hasLocationPermission) {
                            showRemoteDevicePicker = true
                            viewModel.startRemoteScan()
                        } else {
                            permissionLauncher.launch(requiredPermissions())
                            showPermissionDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = if (uiState.isRemoteScanning) "Scanning Remote..." else "Connect Remote",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 9.sp),
                    )
                }
            }

            uiState.selectedDevice?.let { device ->
                Text(
                    text = "Desk: ${device.name ?: device.address}",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            uiState.selectedRemoteDevice?.let { device ->
                Text(
                    text = "Remote: ${device.name ?: device.address}",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val currentError = uiState.error
            if (currentError != null) {
                Text(
                    text = errorMessage(currentError),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor(),
                )
            }

            if (isDeskConnected) {
                Text(
                    text = "Hold to move",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HoldCommandButton(
                        label = "Up",
                        enabled = true,
                        onPress = { viewModel.startContinuousCommand(DeskCommand.Up) },
                        onRelease = { viewModel.stopContinuousCommand() },
                    )
                    HoldCommandButton(
                        label = "Down",
                        enabled = true,
                        onPress = { viewModel.startContinuousCommand(DeskCommand.Down) },
                        onRelease = { viewModel.stopContinuousCommand() },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CommandButton("Memory 1", enabled = true) {
                        viewModel.sendCommand(DeskCommand.Memory(DeskMemorySlot.One))
                    }
                    CommandButton("Memory 2", enabled = true) {
                        viewModel.sendCommand(DeskCommand.Memory(DeskMemorySlot.Two))
                    }
                    CommandButton("Memory 3", enabled = true) {
                        viewModel.sendCommand(DeskCommand.Memory(DeskMemorySlot.Three))
                    }
                }
            }
            if (!isDeskConnected) {
                Text(
                    text = "Connect desk to enable movement controls.",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor(),
                )
            }
        }
    }

    if (showDeskDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDeskDisconnectDialog = false },
            title = { Text("Disconnect Desk", color = primaryTextColor()) },
            text = {
                Text(
                    "Are you sure you want to disconnect from the desk?",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.disconnectDesk()
                    showDeskDisconnectDialog = false
                }) {
                    Text("Disconnect", color = eInkTextColorOr(Color.Red))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeskDisconnectDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showRemoteDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showRemoteDisconnectDialog = false },
            title = { Text("Disconnect Remote", color = primaryTextColor()) },
            text = {
                Text(
                    "Are you sure you want to disconnect from the remote?",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.disconnectRemote()
                    showRemoteDisconnectDialog = false
                }) {
                    Text("Disconnect", color = eInkTextColorOr(Color.Red))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoteDisconnectDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    if (showDeskDevicePicker) {
        DeskDevicePicker(
            title = "Select Desk",
            devices = uiState.devices,
            isScanning = uiState.isScanning,
            selectedDeviceAddress = uiState.selectedDevice?.address,
            onDismiss = {
                viewModel.stopDeskScan()
                showDeskDevicePicker = false
            },
            onSelectDevice = { device ->
                viewModel.selectDeskDevice(device)
                viewModel.connectSelectedDeskDevice()
                showDeskDevicePicker = false
            },
            onRescan = {
                if (uiState.hasScanPermission && uiState.hasConnectPermission && uiState.hasLocationPermission) {
                    viewModel.startDeskScan()
                } else {
                    permissionLauncher.launch(requiredPermissions())
                    showPermissionDialog = true
                }
            },
        )
    }

    if (showRemoteDevicePicker) {
        DeskDevicePicker(
            title = "Select Remote",
            devices = uiState.remoteDevices,
            isScanning = uiState.isRemoteScanning,
            selectedDeviceAddress = uiState.selectedRemoteDevice?.address,
            onDismiss = {
                viewModel.stopRemoteScan()
                showRemoteDevicePicker = false
            },
            onSelectDevice = { device ->
                viewModel.selectRemoteDevice(device)
                viewModel.connectSelectedRemoteDevice()
                showRemoteDevicePicker = false
            },
            onRescan = {
                if (uiState.hasScanPermission && uiState.hasConnectPermission && uiState.hasLocationPermission) {
                    viewModel.startRemoteScan()
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
            title = { Text("Permissions required", color = primaryTextColor()) },
            text = {
                Text(
                    "Please enable Location and Nearby devices permissions in Settings to scan for desks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor(),
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
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .then(clickableModifier)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
            color = secondaryTextColor(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
