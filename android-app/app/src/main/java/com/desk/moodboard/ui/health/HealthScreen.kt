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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
// duplicate imports removed
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import com.desk.moodboard.R
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.AccentOrange
import com.desk.moodboard.ui.theme.appBackgroundColor
import com.desk.moodboard.ui.theme.appSurfaceColor
import com.desk.moodboard.ui.theme.eInkTextColorOr
import com.desk.moodboard.ui.theme.primaryTextColor
import com.desk.moodboard.ui.theme.secondaryTextColor
import com.desk.moodboard.ui.theme.YogurtBlue as AccentRed
import com.desk.moodboard.ui.theme.YogurtPeach as AccentPeach
import com.desk.moodboard.ui.theme.YogurtSilk
import com.desk.moodboard.ui.theme.YogurtNavy
import com.desk.moodboard.ui.theme.YogurtGrey as OldYogurtGrey
import java.util.concurrent.Executors
import androidx.lifecycle.viewmodel.compose.viewModel
import com.desk.moodboard.ui.posture.PostureViewModel
import com.desk.moodboard.ml.PostureState
import com.desk.moodboard.ml.PoseOverlay
import androidx.compose.runtime.collectAsState
import com.desk.moodboard.ui.desk.DeskControlCard
import com.desk.moodboard.ui.desk.DeskControlViewModel
import org.koin.androidx.compose.koinViewModel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HealthScreen(postureViewModel: PostureViewModel = viewModel()) {
    val context = LocalContext.current
    val deskControlViewModel: DeskControlViewModel = koinViewModel()
    var showCamera by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showCamera = true
        } else {
            permissionDenied = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = appBackgroundColor(),
    ) {
        val scroll = rememberScrollState()

        val isMonitoring by postureViewModel.isServiceRunning.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = Dimens.screenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
        ) {
            Text(
                text = stringResource(R.string.health_title),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = primaryTextColor(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.8f),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
                ) {
                    PostureTrendCard(
                        isRecording = isMonitoring,
                        onViewCamera = {
                            val status = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            if (status == PackageManager.PERMISSION_GRANTED) {
                                showCamera = true
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )
                    HydrationCard()
                }

                Column(
                    modifier = Modifier
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Dimens.sectionSpacing),
                ) {
                    DeskControlCard(viewModel = deskControlViewModel)
                    HealthScoreCard()
                    ReminderCard()
                }
            }
        }

        if (showCamera) {
            CameraPreviewDialog(
                postureViewModel = postureViewModel,
                onClose = { 
                    showCamera = false
                    cameraReady = false
                },
                onPermissionDenied = { permissionDenied = true }
            )
        }

        if (permissionDenied) {
            PermissionDialog(onDismiss = { permissionDenied = false }, onRetry = { permissionDenied = false; showCamera = true })
        }
    }
}

@Composable
private fun PostureTrendCard(isRecording: Boolean, onViewCamera: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .background(appSurfaceColor(), RoundedCornerShape(Dimens.cardCorner))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    RoundedCornerShape(Dimens.cardCorner)
                )
        ) {
            Column(
                modifier = Modifier.padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.health_trend_title),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = primaryTextColor(),
                        )
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .background(AccentRed.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(6.dp).background(AccentRed, CircleShape))
                                    Text(
                                        stringResource(R.string.health_recording),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 8.sp,
                                            color = eInkTextColorOr(AccentRed),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = onViewCamera,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentOrange,
                            contentColor = eInkTextColorOr(Color.White),
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            stringResource(R.string.health_view_camera),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp)
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.health_weekly_improvements),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = secondaryTextColor(),
                )
                LineChart(
                    values = listOf(0.65f, 0.72f, 0.68f, 0.78f, 0.82f, 0.75f, 0.88f),
                    labels = listOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY,
                        DayOfWeek.SATURDAY,
                        DayOfWeek.SUNDAY
                    ).map { it.getDisplayName(TextStyle.NARROW, Locale.getDefault()) },
                )
            }
        }
    }
}

@Composable
private fun LineChart(values: List<Float>, labels: List<String>) {
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(vertical = 8.dp)
        ) {
            val width = size.width
            val height = size.height
            val spacing = width / (values.size - 1)
            
            // Draw path
            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { index, value ->
                val x = index * spacing
                val y = height - (value * height)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            
            drawPath(
                path = path,
                color = AccentOrange,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            
            // Draw points
            values.forEachIndexed { index, value ->
                val x = index * spacing
                val y = height - (value * height)
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    color = AccentOrange,
                    radius = 3.dp.toPx(),
                    center = Offset(x, y),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor(),
                    fontSize = 10.sp,
                    modifier = Modifier.width(24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun BarChart(values: List<Float>, labels: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
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
                        .width(24.dp)
                        .height(80.dp)
                        .background(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .height(80.dp * value)
                            .background(AccentOrange.copy(alpha = 0.8f), RoundedCornerShape(6.dp)),
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryTextColor(),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun HydrationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .background(appSurfaceColor(), RoundedCornerShape(Dimens.cardCorner))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    RoundedCornerShape(Dimens.cardCorner)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.cardPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.health_hydration_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = primaryTextColor(),
                    )
                    Text(
                        text = stringResource(R.string.health_hydration_target),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = secondaryTextColor(),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ProgressBar(fraction = 0.32f, color = AccentRed.copy(alpha = 0.7f), height = 6.dp)
                    Text(
                        text = stringResource(R.string.health_hydration_logged),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = primaryTextColor(),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Icon(imageVector = Icons.Outlined.LocalDrink, contentDescription = null, tint = AccentRed.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun HealthScoreCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .background(appSurfaceColor(), RoundedCornerShape(Dimens.cardCorner))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(Dimens.cardCorner)
                )
        ) {
            Column(
                modifier = Modifier.padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.health_score_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = primaryTextColor()
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
                        val outlineArcColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        Canvas(modifier = Modifier.size(90.dp)) {
                            drawArc(
                                color = outlineArcColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                            // Progress arc for score 68
                            drawArc(
                                color = AccentOrange,
                                startAngle = -90f,
                                sweepAngle = 360f * 0.68f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "68",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 21.sp),
                                color = primaryTextColor()
                            )
                            Text(
                                text = stringResource(R.string.health_score_label),
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, fontSize = 10.sp),
                                color = secondaryTextColor()
                            )
                        }
                    }
                    
                    // Status Pill
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.health_status_label),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp, fontSize = 10.sp),
                            color = secondaryTextColor(),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).background(AccentOrange, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.cardCorner),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .background(appSurfaceColor(), RoundedCornerShape(Dimens.cardCorner))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    RoundedCornerShape(Dimens.cardCorner)
                )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.health_reminders_title),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = primaryTextColor(),
                )
                ReminderRow(stringResource(R.string.health_reminder_stretch), stringResource(R.string.health_reminder_stretch_cadence), AccentOrange)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ReminderRow(stringResource(R.string.health_reminder_water), stringResource(R.string.health_reminder_water_cadence), AccentRed)
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ReminderRow(stringResource(R.string.health_reminder_stand), stringResource(R.string.health_reminder_stand_cadence), AccentOrange)
            }
        }
    }
}

@Composable
private fun PermissionDialog(onDismiss: () -> Unit, onRetry: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.health_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.health_close))
            }
        },
        title = { Text(stringResource(R.string.health_camera_permission_title)) },
        text = { Text(stringResource(R.string.health_camera_permission_body)) }
    )
}

@Composable
private fun CameraPreviewDialog(
    postureViewModel: PostureViewModel,
    onClose: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasPermission by remember { mutableStateOf(false) }
    var permissionChecked by remember { mutableStateOf(false) }

    val currentResult by postureViewModel.currentResult.collectAsState()
    val poseOverlay by postureViewModel.poseOverlay.collectAsState()

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        hasPermission = granted
        permissionChecked = true
        if (!granted) onPermissionDenied()
    }

    if (!permissionChecked) return
    if (!hasPermission) return

    Dialog(onDismissRequest = onClose) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = YogurtSilk,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.health_posture_camera),
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryTextColor(),
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.content_desc_close),
                            tint = secondaryTextColor(),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Color.Black, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                // Use the background service's already running preview
                                postureViewModel.attachPreviewToService(this.surfaceProvider)
                            }
                        },
                        onRelease = {
                            // Detach when dialog is closed
                            postureViewModel.detachPreviewFromService()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                    )
                    // Landmark overlay
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .align(Alignment.Center)
                    ) {
                        val overlay: PoseOverlay = poseOverlay ?: return@Canvas
                        val points = overlay.landmarks
                        if (points.isEmpty()) return@Canvas

                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        fun map(lm: com.google.mediapipe.tasks.components.containers.NormalizedLandmark): Offset {
                            // Since the image MediaPipe sees is already rotated and mirrored,
                            // the landmarks it returns are already in "Mirror Space".
                            // No extra flip needed.
                            return Offset(lm.x() * canvasWidth, lm.y() * canvasHeight)
                        }

                        poseConnections.forEach { (a, b) ->
                            if (a < points.size && b < points.size) {
                                drawLine(
                                    color = Color(0xFF4CAF50),
                                    strokeWidth = 2.dp.toPx(),
                                    start = map(points[a]),
                                    end = map(points[b]),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        points.forEach { lm ->
                            drawCircle(
                                color = Color.Cyan,
                                radius = 3.dp.toPx(),
                                center = map(lm)
                            )
                        }
                    }
                    
                    // HUD Overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = currentResult.state.label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = eInkTextColorOr(
                                if (currentResult.state == PostureState.SITTING_STRAIGHT || currentResult.state == PostureState.RECLINING) Color.Green
                                else Color.Red
                            )
                        )
                        Text(
                            text = stringResource(
                                R.string.health_confidence,
                                (currentResult.confidence * 100).toInt()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = eInkTextColorOr(Color.White.copy(alpha = 0.8f))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.health_live_preview),
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryTextColor()
                )
            }
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
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp),
                color = primaryTextColor()
            )
            Text(
                text = cadence,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = secondaryTextColor()
            )
        }
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = stringResource(R.string.health_toggle_on),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = eInkTextColorOr(color)
            )
        }
    }
}

// Minimal connection map for skeleton overlay (MediaPipe pose indices).
private val poseConnections = listOf(
    11 to 12, 11 to 23, 12 to 24, 23 to 24,
    23 to 25, 24 to 26, 25 to 27, 26 to 28,
    27 to 29, 28 to 30, 29 to 31, 30 to 32,
    11 to 13, 13 to 15, 12 to 14, 14 to 16
)

@Composable
private fun ProgressBar(fraction: Float, color: Color, height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape),
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
                .size(8.dp)
                .background(color.copy(alpha = 0.7f), CircleShape),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = secondaryTextColor(),
            fontSize = 9.sp
        )
    }
}
