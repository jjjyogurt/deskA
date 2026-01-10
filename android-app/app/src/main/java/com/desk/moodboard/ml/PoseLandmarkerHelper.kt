package com.desk.moodboard.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseLandmarkerHelper(
    val context: Context,
    val runningMode: RunningMode = RunningMode.LIVE_STREAM,
    val minPoseDetectionConfidence: Float = 0.5f,
    val minPoseTrackingConfidence: Float = 0.5f,
    val minPosePresenceConfidence: Float = 0.5f,
    var landmarkerListener: LandmarkerListener? = null
) {

    private var poseLandmarker: PoseLandmarker? = null

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    fun isClose(): Boolean {
        return poseLandmarker == null
    }

    private fun setupPoseLandmarker() {
        val baseOptionBuilder = BaseOptions.builder()
        baseOptionBuilder.setDelegate(Delegate.CPU)
        baseOptionBuilder.setModelAssetPath("pose_landmarker.task")

        try {
            val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptionBuilder.build())
                .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                .setMinTrackingConfidence(minPoseTrackingConfidence)
                .setMinPosePresenceConfidence(minPosePresenceConfidence)
                .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            landmarkerListener?.onError(
                "Pose landmarker failed to initialize. See error logs for details"
            )
            Log.e(TAG, "MediaPipe error: ${e.message}")
        } catch (e: RuntimeException) {
            landmarkerListener?.onError(
                "Pose landmarker failed to initialize. See error logs for details"
            )
            Log.e(TAG, "Image classifier failed to load model with error: ${e.message}")
        }
    }

    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream while not using RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val originalBitmap = imageProxy.toBitmap() ?: return
        
        // 1. ROTATE AND FLIP MANUALLY
        // This ensures the image MediaPipe sees is EXACTLY what you see on screen
        // (Upright and Mirrored).
        val matrix = Matrix().apply {
            // First, rotate to upright (usually 270 on front cam)
            postRotate(rotationDegrees.toFloat())
            // Then, flip horizontally if it's the front camera
            if (isFrontCamera) {
                postScale(-1f, 1f, originalBitmap.width / 2f, originalBitmap.height / 2f)
            }
        }
        
        val processedBitmap = Bitmap.createBitmap(
            originalBitmap, 0, 0,
            originalBitmap.width, originalBitmap.height,
            matrix, true
        ).also {
            if (it != originalBitmap) originalBitmap.recycle()
        }

        // 2. TELL MEDIAPIPE ROTATION IS 0
        // Because we already rotated the bitmap ourselves.
        val imageOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(0)
            .build()

        val bitmapBuffer = BitmapImageBuilder(processedBitmap).build()

        try {
            detectAsync(bitmapBuffer, imageOptions, frameTime)
        } catch (t: Throwable) {
            Log.e(TAG, "detectAsync failed", t)
        }
    }

    private fun detectAsync(mpImage: MPImage, imageOptions: ImageProcessingOptions, frameTime: Long) {
        poseLandmarker?.detectAsync(mpImage, imageOptions, frameTime)
    }

    private fun returnLivestreamResult(
        result: PoseLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        landmarkerListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime,
                input.height,
                input.width
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        landmarkerListener?.onError(error.message ?: "An unknown error has occurred")
    }

    // Extension to convert ImageProxy to Bitmap
    private fun ImageProxy.toBitmap(): Bitmap? {
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        
        return if (rowPadding > 0) {
            Bitmap.createBitmap(bitmap, 0, 0, width, height).also {
                if (it != bitmap) bitmap.recycle()
            }
        } else {
            bitmap
        }
    }

    companion object {
        private const val TAG = "PoseLandmarkerHelper"
    }

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String)
        fun onResults(resultBundle: ResultBundle)
    }
}
