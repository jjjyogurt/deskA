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
import com.desk.moodboard.ui.theme.BackgroundGrey
import com.desk.moodboard.ui.theme.Dimens
import com.desk.moodboard.ui.theme.FillGrey
import com.desk.moodboard.ui.theme.TextDark
import com.desk.moodboard.ui.theme.TextGrey
import com.desk.moodboard.ui.theme.AccentOrange
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

@Composable
fun HealthScreen(postureViewModel: PostureViewModel = viewModel()) {
    val context = LocalContext.current
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
        color = BackgroundGrey,
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
                text = "Health Overview",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = TextDark,
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
                .background(Color.White, RoundedCornerShape(Dimens.cardCorner))
                .border(1.dp, FillGrey.copy(alpha = 0.6f), RoundedCornerShape(Dimens.cardCorner))
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
                        Text(text = "Health Trend", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextDark)
                        if (isRecording) {
                            Box(
                                modifier = Modifier
                                    .background(AccentRed.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(modifier = Modifier.size(6.dp).background(AccentRed, CircleShape))
                                    Text("Recording", style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, color = AccentRed, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                    Button(
                        onClick = onViewCamera,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentOrange, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text("View Camera", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp))
                    }
                }
                Text(
                    text = "Weekly improvements.",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = TextGrey,
                )
                LineChart(
                    values = listOf(0.65f, 0.72f, 0.68f, 0.78f, 0.82f, 0.75f, 0.88f),
                    labels = listOf("M", "T", "W", "T", "F", "S", "S"),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Legend(color = AccentOrange, text = "Heal Score")
                }
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
                    color = TextGrey,
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
                        .background(FillGrey.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
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
                Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextGrey, fontSize = 10.sp)
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
                .background(Color.White, RoundedCornerShape(Dimens.cardCorner))
                .border(1.dp, FillGrey.copy(alpha = 0.6f), RoundedCornerShape(Dimens.cardCorner))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.cardPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = "Hydration", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = TextDark)
                    Text(
                        text = "Target 2000 ml",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextGrey,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ProgressBar(fraction = 0.32f, color = AccentRed.copy(alpha = 0.7f), height = 6.dp)
                    Text(
                        text = "640 ml logged",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                        color = TextDark,
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
                .background(Color.White, RoundedCornerShape(Dimens.cardCorner))
                .border(width = 1.dp, color = FillGrey.copy(alpha = 0.6f), shape = RoundedCornerShape(Dimens.cardCorner))
        ) {
            Column(
                modifier = Modifier.padding(Dimens.cardPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                color = FillGrey.copy(alpha = 0.3f),
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
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .background(FillGrey.copy(alpha = 0.5f), CircleShape)
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
                .background(Color.White, RoundedCornerShape(Dimens.cardCorner))
                .border(1.dp, FillGrey.copy(alpha = 0.6f), RoundedCornerShape(Dimens.cardCorner))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "Reminders", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = TextDark)
                ReminderRow("Stretch", "45 min", AccentOrange)
                HorizontalDivider(color = FillGrey.copy(alpha = 0.4f))
                ReminderRow("Water", "60 min", AccentRed)
                HorizontalDivider(color = FillGrey.copy(alpha = 0.4f))
                ReminderRow("Stand", "3 x day", AccentOrange)
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
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = { Text("Camera permission needed") },
        text = { Text("Please grant camera permission to view the posture camera.") }
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
                    Text("Posture Camera", style = MaterialTheme.typography.titleMedium, color = TextDark)
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = "Close", tint = TextGrey)
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
                            color = if (currentResult.state == PostureState.SITTING_STRAIGHT || currentResult.state == PostureState.RECLINING) Color.Green else Color.Red
                        )
                        Text(
                            text = "Confidence: ${(currentResult.confidence * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Live posture preview (front camera)",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextGrey
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
            Text(text = title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp), color = TextDark)
            Text(text = cadence, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextGrey)
        }
        Box(
            modifier = Modifier
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(text = "On", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp), color = color)
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
            .background(FillGrey.copy(alpha = 0.3f), CircleShape),
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
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = TextGrey, fontSize = 9.sp)
    }
}
