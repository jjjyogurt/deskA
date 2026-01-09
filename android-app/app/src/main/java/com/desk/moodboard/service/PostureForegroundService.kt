package com.desk.moodboard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.content.res.Configuration
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.AspectRatio
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.desk.moodboard.ml.PoseLandmarkerHelper
import com.desk.moodboard.ml.PostureAnalyzer
import com.desk.moodboard.ml.PoseOverlay
import com.desk.moodboard.ml.PostureClassifier
import com.desk.moodboard.ml.PostureResult
import com.desk.moodboard.ml.PostureState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PostureForegroundService : Service(), LifecycleOwner, PoseLandmarkerHelper.LandmarkerListener {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val binder = LocalBinder()
    
    private lateinit var cameraExecutor: ExecutorService
    private var poseLandmarkerHelper: PoseLandmarkerHelper? = null
    private var classifier: PostureClassifier? = null
    private var preview: Preview? = null
    private var pendingSurfaceProvider: Preview.SurfaceProvider? = null
    
    private val _currentResult = MutableStateFlow(PostureResult(PostureState.UNKNOWN, 0f))
    val currentResult = _currentResult.asStateFlow()
    private val _poseOverlay = MutableStateFlow<PoseOverlay?>(null)
    val poseOverlay = _poseOverlay.asStateFlow()
    private var lastLmLog = 0L
    private val lmLogIntervalMs = 1500L

    inner class LocalBinder : Binder() {
        fun getService(): PostureForegroundService = this@PostureForegroundService
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun setPreviewSurfaceProvider(provider: Preview.SurfaceProvider?) {
        Log.d(TAG, "Setting surface provider: ${provider != null}")
        pendingSurfaceProvider = provider
        preview?.setSurfaceProvider(provider)
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        cameraExecutor = Executors.newSingleThreadExecutor()
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
        
        setupML()
        startCamera()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        Log.d(TAG, "onStartCommand -> lifecycle RESUMED")
        return START_STICKY
    }

    private fun setupML() {
        Log.d(TAG, "setupML start")
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = this,
            // Increased to 0.6f to prevent background "hallucinations" in empty frames
            minPoseDetectionConfidence = 0.6f,
            minPoseTrackingConfidence = 0.6f,
            minPosePresenceConfidence = 0.6f,
            landmarkerListener = this
        )
        classifier = PostureClassifier(this)
        Log.d(TAG, "setupML done")
    }

    fun startCamera() {
        Log.d(TAG, "startCamera called")
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Prefer landscape; align rotation/aspect similar to MediaPipe sample
                val rotation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Surface.ROTATION_90
                } else {
                    Surface.ROTATION_0
                }

                val helper = poseLandmarkerHelper
                if (helper == null) {
                    Log.e(TAG, "poseLandmarkerHelper is null; cannot bind analysis")
                    return@addListener
                }

                // Initialize Preview use case
                preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setTargetRotation(rotation)
                    .build().also { builtPreview ->
                        // If UI requested a surface before preview existed, apply it now
                        pendingSurfaceProvider?.let { builtPreview.setSurfaceProvider(it) }
                    }

                // Initialize ImageAnalysis use case
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setTargetRotation(rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, PostureAnalyzer(helper))
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                // Bind both Preview and ImageAnalysis to the service lifecycle
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
                Log.d(
                    TAG,
                    "Background camera and analysis started successfully. rotation=$rotation provider=${pendingSurfaceProvider != null}"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        val poseResult = resultBundle.results.firstOrNull()
        if (poseResult != null) {
            val landmarks = poseResult.landmarks().firstOrNull()
            val count = landmarks?.size ?: 0
            Log.d(TAG, "Landmarks count=$count")
            if (count == 0) {
                Log.w(TAG, "No landmarks detected - clearing state")
                _currentResult.value = PostureResult(PostureState.UNKNOWN, 0f)
                _poseOverlay.value = null
                updateNotification("Posture Monitor Active")
                return
            }
            val classification = classifier?.classify(poseResult)
            if (classification != null) {
                _currentResult.value = classification.copy(inferenceTime = resultBundle.inferenceTime)
                _poseOverlay.value = PoseOverlay(
                    landmarks = ArrayList(landmarks), // copy so data survives after callback
                    imageWidth = resultBundle.inputImageWidth,
                    imageHeight = resultBundle.inputImageHeight,
                    isFrontCamera = true
                )
                updateNotification("Current Posture: ${classification.state.label}")
                Log.d(TAG, "Result state=${classification.state} conf=${classification.confidence}")
            }
            // Throttled landmark debug log to verify motion and ranges
            val now = SystemClock.uptimeMillis()
            if (landmarks != null && now - lastLmLog >= lmLogIntervalMs) {
                lastLmLog = now
                val nose = landmarks.getOrNull(0)
                val lShoulder = landmarks.getOrNull(11)
                val rShoulder = landmarks.getOrNull(12)
                val lHip = landmarks.getOrNull(23)
                val rHip = landmarks.getOrNull(24)

                val xs = landmarks.map { it.x() }
                val ys = landmarks.map { it.y() }
                val zs = landmarks.map { it.z() }

                Log.d(
                    "PoseDebug",
                    "Nose=${nose?.x()},${nose?.y()},${nose?.z()} " +
                            "LShoulder=${lShoulder?.x()},${lShoulder?.y()},${lShoulder?.z()} " +
                            "RShoulder=${rShoulder?.x()},${rShoulder?.y()},${rShoulder?.z()} " +
                            "LHip=${lHip?.x()},${lHip?.y()},${lHip?.z()} " +
                            "RHip=${rHip?.x()},${rHip?.y()},${rHip?.z()} " +
                            "RangeX=${xs.minOrNull()}..${xs.maxOrNull()} " +
                            "RangeY=${ys.minOrNull()}..${ys.maxOrNull()} " +
                            "RangeZ=${zs.minOrNull()}..${zs.maxOrNull()}"
                )
            }
        } else {
            Log.d(TAG, "No poseResult in bundle")
        }
    }

    override fun onError(error: String) {
        Log.e(TAG, "MediaPipe Error: $error")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Posture Monitoring",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Posture Monitor Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cameraExecutor.shutdown()
        poseLandmarkerHelper?.clearPoseLandmarker()
        classifier?.close()
        pendingSurfaceProvider = null
        preview = null
        Log.d(TAG, "Service destroyed and resources cleared")
    }

    companion object {
        private const val TAG = "PostureService"
        private const val CHANNEL_ID = "posture_monitor_channel"
        private const val NOTIFICATION_ID = 1001
    }
}



