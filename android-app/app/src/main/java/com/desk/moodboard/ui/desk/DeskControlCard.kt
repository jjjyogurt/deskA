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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
        val hasAdvertisePermission = hasAdvertisePermission(results, context)
        val hasLocationPermission = hasLocationPermission(results, context)
        viewModel.updatePermissions(
            hasScanPermission = hasScanPermission,
            hasConnectPermission = hasConnectPermission,
            hasAdvertisePermission = hasAdvertisePermission,
            hasLocationPermission = hasLocationPermission,
        )
        if (!(hasScanPermission && hasConnectPermission && hasAdvertisePermission && hasLocationPermission)) {
            showPermissionDialog = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.updatePermissions(
            hasScanPermission = hasScanPermission(emptyMap(), context),
            hasConnectPermission = hasConnectPermission(emptyMap(), context),
            hasAdvertisePermission = hasAdvertisePermission(emptyMap(), context),
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
                .padding(horizontal = Dimens.cardPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val isDeskConnected = uiState.connectionState is DeskConnectionState.Connected
            val isRemoteConnected = uiState.remoteConnectionState is DeskConnectionState.Connected

            // Header with Title and Status Indicators
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

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ConnectionIndicator(
                        label = "DESK",
                        isConnected = isDeskConnected,
                        onClick = {
                            if (isDeskConnected) {
                                showDeskDisconnectDialog = true
                            } else {
                                if (uiState.hasScanPermission && uiState.hasConnectPermission) {
                                    showDeskDevicePicker = true
                                    viewModel.startDeskScan()
                                } else {
                                    permissionLauncher.launch(requiredPermissions())
                                }
                            }
                        }
                    )
                    ConnectionIndicator(
                        label = "REMOTE",
                        isConnected = isRemoteConnected,
                        onClick = {
                            if (isRemoteConnected) {
                                showRemoteDisconnectDialog = true
                            } else {
                                if (uiState.hasScanPermission && uiState.hasConnectPermission) {
                                    showRemoteDevicePicker = true
                                    viewModel.startRemoteScan()
                                } else {
                                    permissionLauncher.launch(requiredPermissions())
                                }
                            }
                        }
                    )
                }
            }

            // Errors
            uiState.error?.let { error ->
                Text(
                    text = errorMessage(error),
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor(),
                )
            }

            // Movement Controls
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HoldCommandButton(
                        label = "UP",
                        icon = Icons.Default.KeyboardArrowUp,
                        enabled = isDeskConnected,
                        onClick = { viewModel.toggleMotion(DeskCommand.Up) },
                    )
                    HoldCommandButton(
                        label = "DOWN",
                        icon = Icons.Default.KeyboardArrowDown,
                        enabled = isDeskConnected,
                        onClick = { viewModel.toggleMotion(DeskCommand.Down) },
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PresetButton(
                        label = "P1",
                        enabled = isDeskConnected,
                        onClick = { viewModel.sendCommand(DeskCommand.Memory(DeskMemorySlot.One)) }
                    )
                    PresetButton(
                        label = "P2",
                        enabled = isDeskConnected,
                        onClick = { viewModel.sendCommand(DeskCommand.Memory(DeskMemorySlot.Two)) }
                    )
                    PresetButton(
                        label = "P3",
                        enabled = isDeskConnected,
                        onClick = { viewModel.sendCommand(DeskCommand.Memory(DeskMemorySlot.Three)) }
                    )
                }
            }
        }
    }

    // Dialogs
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
private fun ConnectionIndicator(
    label: String,
    isConnected: Boolean,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isConnected) {
        AccentOrange.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    
    val contentColor = if (isConnected) {
        AccentOrange
    } else {
        secondaryTextColor()
    }

    val borderColor = if (isConnected) AccentOrange.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Row(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = contentColor
        )
        Icon(
            imageVector = if (isConnected) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = contentColor
        )
    }
}

@Composable
private fun RowScope.HoldCommandButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier
            .weight(1f)
            .height(80.dp),
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentOrange,
            contentColor = Color.White,
            disabledContainerColor = AccentOrange.copy(alpha = 0.3f),
            disabledContentColor = Color.White.copy(alpha = 0.5f)
        ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            )
        }
    }
}

@Composable
private fun RowScope.PresetButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        modifier = Modifier
            .weight(1f)
            .height(40.dp),
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            contentColor = primaryTextColor(),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            disabledContentColor = secondaryTextColor()
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        )
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
            Manifest.permission.BLUETOOTH_ADVERTISE,
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

private fun hasAdvertisePermission(
    results: Map<String, Boolean>,
    context: android.content.Context,
): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        results[Manifest.permission.BLUETOOTH_ADVERTISE] ?: hasPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
        true
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
