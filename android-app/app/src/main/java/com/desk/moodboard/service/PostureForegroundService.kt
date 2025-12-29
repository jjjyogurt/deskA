package com.desk.moodboard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
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
            // Lower thresholds temporarily to ensure detection in low confidence scenarios
            minPoseDetectionConfidence = 0.3f,
            minPoseTrackingConfidence = 0.3f,
            minPosePresenceConfidence = 0.3f,
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
                Log.w(TAG, "No landmarks detected")
                return
            }
            val classification = classifier?.classify(poseResult)
            if (classification != null) {
                _currentResult.value = classification.copy(inferenceTime = resultBundle.inferenceTime)
                updateNotification("Current Posture: ${classification.state.label}")
                Log.d(TAG, "Result state=${classification.state} conf=${classification.confidence}")
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



