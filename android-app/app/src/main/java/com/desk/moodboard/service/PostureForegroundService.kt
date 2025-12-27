package com.desk.moodboard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
    
    private val _currentResult = MutableStateFlow(PostureResult(PostureState.UNKNOWN, 0f))
    val currentResult = _currentResult.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): PostureForegroundService = this@PostureForegroundService
    }

    override fun onBind(intent: Intent): IBinder = binder

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
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        return START_STICKY
    }

    private fun setupML() {
        poseLandmarkerHelper = PoseLandmarkerHelper(
            context = this,
            landmarkerListener = this
        )
        classifier = PostureClassifier(this)
    }

    fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, PostureAnalyzer(poseLandmarkerHelper!!))
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
                Log.d(TAG, "Background camera started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
        val poseResult = resultBundle.results.firstOrNull()
        if (poseResult != null) {
            val classification = classifier?.classify(poseResult)
            if (classification != null) {
                _currentResult.value = classification.copy(inferenceTime = resultBundle.inferenceTime)
                updateNotification("Current Posture: ${classification.state.label}")
            }
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
    }

    companion object {
        private const val TAG = "PostureService"
        private const val CHANNEL_ID = "posture_monitor_channel"
        private const val NOTIFICATION_ID = 1001
    }
}



