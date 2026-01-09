package com.desk.moodboard.data

import android.util.Log
import com.desk.moodboard.ml.PostureResult
import com.desk.moodboard.ml.PostureState
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class PostureRepository {

    private var db: FirebaseFirestore? = null
    private var lastState: PostureState = PostureState.UNKNOWN
    private var stateStartTime: Long = 0L
    private val STABILITY_THRESHOLD_MS = 5000L

    init {
        // SAFE INITIALIZATION: Catch Throwable to ensure it NEVER crashes the app
        try {
            db = FirebaseFirestore.getInstance()
            Log.d("PostureRepo", "Firebase Firestore initialized.")
        } catch (e: Throwable) { 
            Log.e("PostureRepo", "Firebase not ready: ${e.message}. Firestore logging disabled.")
            db = null
        }
    }

    fun onPostureResult(result: PostureResult) {
        val newState = result.state
        val currentTime = System.currentTimeMillis()

        if (newState != lastState) {
            if (lastState != PostureState.UNKNOWN && (currentTime - stateStartTime) >= STABILITY_THRESHOLD_MS) {
                logStablePosture(lastState, stateStartTime, currentTime)
            }
            lastState = newState
            stateStartTime = currentTime
        }
    }

    private fun logStablePosture(state: PostureState, start: Long, end: Long) {
        // ONLY log if db is actually working
        val dbInstance = db ?: return 
        
        val entry = hashMapOf(
            "state" to state.name,
            "label" to state.label,
            "startTime" to Date(start),
            "endTime" to Date(end),
            "durationMs" to (end - start),
            "timestamp" to Date()
        )

        dbInstance.collection("posture_sessions")
            .add(entry)
            .addOnFailureListener { e -> Log.e("PostureRepo", "Log failed", e) }
    }

    fun startSession() { stateStartTime = System.currentTimeMillis(); lastState = PostureState.UNKNOWN }
    fun endSession() { 
        val currentTime = System.currentTimeMillis()
        if (lastState != PostureState.UNKNOWN && (currentTime - stateStartTime) >= STABILITY_THRESHOLD_MS) {
            logStablePosture(lastState, stateStartTime, currentTime)
        }
    }
}












