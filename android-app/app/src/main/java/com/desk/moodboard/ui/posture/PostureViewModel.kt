package com.desk.moodboard.ui.posture

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.desk.moodboard.data.PostureRepository
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

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    private var postureService: PostureForegroundService? = null
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PostureForegroundService.LocalBinder
            postureService = binder.getService()
            _isServiceRunning.value = true
            
            viewModelScope.launch {
                postureService?.currentResult?.collect { result ->
                    _currentResult.value = result
                    repository.onPostureResult(result)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            postureService = null
            _isServiceRunning.value = false
        }
    }

    fun togglePostureMonitoring(enable: Boolean) {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, PostureForegroundService::class.java)
        
        if (enable) {
            context.startForegroundService(intent)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            repository.startSession()
        } else {
            repository.endSession()
            if (_isServiceRunning.value) {
                context.unbindService(serviceConnection)
                _isServiceRunning.value = false
            }
            context.stopService(intent)
            postureService = null
        }
    }

    fun requestCameraRestart() {
        postureService?.startCamera()
    }

    override fun onCleared() {
        super.onCleared()
        if (_isServiceRunning.value) {
            getApplication<Application>().applicationContext.unbindService(serviceConnection)
        }
    }
}



