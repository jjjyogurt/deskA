package com.desk.moodboard.ui.posture

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.camera.core.Preview
import com.desk.moodboard.data.PostureRepository
import com.desk.moodboard.ml.PoseOverlay
import com.desk.moodboard.ml.PostureResult
import com.desk.moodboard.ml.PostureState
import com.desk.moodboard.service.PostureForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PostureViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PostureRepository()
    
    private val _currentResult = MutableStateFlow(PostureResult(PostureState.UNKNOWN, 0f))
    val currentResult = _currentResult.asStateFlow()
    private val _poseOverlay = MutableStateFlow<PoseOverlay?>(null)
    val poseOverlay = _poseOverlay.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    private var postureService: PostureForegroundService? = null
    private var pendingSurfaceProvider: Preview.SurfaceProvider? = null
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PostureForegroundService.LocalBinder
            postureService = binder.getService()
            _isServiceRunning.value = true
            Log.d(TAG, "Service connected")
            pendingSurfaceProvider?.let { provider ->
                postureService?.setPreviewSurfaceProvider(provider)
            }
            
            viewModelScope.launch {
                postureService?.currentResult?.collect { result ->
                    _currentResult.value = result
                    repository.onPostureResult(result)
                }
            }
            viewModelScope.launch {
                postureService?.poseOverlay?.collect { overlay ->
                    _poseOverlay.value = overlay
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            postureService = null
            _isServiceRunning.value = false
            Log.d(TAG, "Service disconnected")
        }
    }

    fun togglePostureMonitoring(enable: Boolean) {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, PostureForegroundService::class.java)
        Log.d(TAG, "togglePostureMonitoring enable=$enable")
        
        if (enable) {
            try {
                context.startForegroundService(intent)
                val bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                Log.d(TAG, "bindService result=$bound")
                _isServiceRunning.value = true // optimistic; will be confirmed by connection
                repository.startSession()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start/bind service", e)
            }
        } else {
            repository.endSession()
            if (_isServiceRunning.value) {
                try {
                    context.unbindService(serviceConnection)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to unbind service", e)
                }
                _isServiceRunning.value = false
            }
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop service", e)
            }
            postureService = null
            Log.d(TAG, "Service stopped")
        }
    }

    fun attachPreviewToService(surfaceProvider: Preview.SurfaceProvider) {
        // Ensure service is running before attaching the provider
        pendingSurfaceProvider = surfaceProvider
        val context = getApplication<Application>().applicationContext
        if (postureService == null) {
            val intent = Intent(context, PostureForegroundService::class.java)
            context.startForegroundService(intent)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        postureService?.setPreviewSurfaceProvider(surfaceProvider)
    }

    fun detachPreviewFromService() {
        pendingSurfaceProvider = null
        postureService?.setPreviewSurfaceProvider(null)
    }

    fun requestCameraRestart() {
        postureService?.startCamera()
    }

    companion object {
        private const val TAG = "PostureViewModel"
    }

    override fun onCleared() {
        super.onCleared()
        if (_isServiceRunning.value) {
            getApplication<Application>().applicationContext.unbindService(serviceConnection)
        }
    }
}



