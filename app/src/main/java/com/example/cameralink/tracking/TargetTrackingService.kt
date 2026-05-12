package com.example.cameralink.tracking

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * Service that manages real-time tracking and motorized control
 */
class TargetTrackingService : Service() {
    private val binder = LocalBinder()
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private lateinit var objectDetector: ObjectDetector
    private lateinit var targetTracker: TargetTracker
    private lateinit var motorController: MotorController
    private lateinit var cloudApi: CloudTrackingApi

    // Live data for UI updates
    val trackingState = MutableLiveData<TrackingState>(
        TrackingState(isTracking = false)
    )
    val detectedObjects = MutableLiveData<List<DetectedObject>>(emptyList())

    private var isTracking = false
    private var selectedTargetId: Int? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("TargetTrackingService", "Service created")

        objectDetector = ObjectDetector(this)
        targetTracker = TargetTracker()
        motorController = MotorController()
        cloudApi = CloudTrackingApi()

        motorController.initialize()
    }

    /**
     * Process camera frame for object detection and tracking
     */
    fun processFrame(imageProxy: ImageProxy) {
        if (!isTracking) return

        scope.launch {
            try {
                val bitmap = imageProxyToBitmap(imageProxy)

                // Real-time detection
                val detections = objectDetector.detectObjects(bitmap)
                val trackedObjects = targetTracker.update(detections)

                detectedObjects.postValue(detections)

                // Update motor position if tracking target
                selectedTargetId?.let { targetId ->
                    trackedObjects[targetId]?.let { target ->
                        updateMotorPosition(target, bitmap.width, bitmap.height)
                        updateTrackingState(target)
                    }
                }

                // Send to cloud for additional processing (non-blocking)
                cloudApi.detectFromCloud(bitmap)
                    .onSuccess { cloudResult ->
                        Log.d("TargetTrackingService", "Cloud detection: ${cloudResult.detections.size} objects")
                    }
                    .onFailure { error ->
                        Log.e("TargetTrackingService", "Cloud detection failed: ${error.message}")
                    }

                bitmap.recycle()
            } catch (e: Exception) {
                Log.e("TargetTrackingService", "Frame processing error: ${e.message}")
            }
        }
    }

    /**
     * Calculate motor angles to center target in frame
     */
    private fun updateMotorPosition(target: DetectedObject, frameWidth: Int, frameHeight: Int) {
        val centerX = target.boundingBox.centerX()
        val centerY = target.boundingBox.centerY()

        // Calculate offset from center
        val offsetX = centerX - frameWidth / 2f
        val offsetY = centerY - frameHeight / 2f

        // Convert pixels to degrees (assuming ~60 degree FOV)
        val pixelsPerDegree = frameWidth / 60f
        val panAngle = offsetX / pixelsPerDegree
        val tiltAngle = -offsetY / pixelsPerDegree  // Negative because y increases downward

        val command = MotorControlCommand(
            panAngle = panAngle.coerceIn(-180f, 180f),
            tiltAngle = tiltAngle.coerceIn(-90f, 90f),
            speed = 70
        )

        motorController.sendCommand(command)
    }

    private fun updateTrackingState(target: DetectedObject) {
        val command = motorController.let { controller ->
            // This is a placeholder - you'll need to expose motor state
            MotorControlCommand(0f, 0f)
        }

        trackingState.postValue(
            TrackingState(
                isTracking = true,
                targetId = selectedTargetId,
                targetBoundingBox = target.boundingBox,
                targetLabel = target.label,
                confidence = target.confidence,
                panAngle = command.panAngle,
                tiltAngle = command.tiltAngle
            )
        )
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val planes = imageProxy.planes
        val ySize = planes[0].buffer.remaining()
        val uvPixelStride = planes[1].pixelStride

        val nv21 = ByteArray(ySize + planes[1].buffer.remaining() + planes[2].buffer.remaining())
        planes[0].buffer.get(nv21, 0, ySize)
        val uvBuffer = if (uvPixelStride == 1) {
            planes[1].buffer.also { planes[2].buffer.get(it) }
        } else {
            planes[1].buffer
        }
        uvBuffer.get(nv21, ySize, uvBuffer.remaining())

        val width = imageProxy.width
        val height = imageProxy.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // TODO: Implement NV21 to ARGB conversion
        return bitmap
    }

    fun startTracking() {
        isTracking = true
        motorController.home()
        Log.d("TargetTrackingService", "Tracking started")
    }

    fun stopTracking() {
        isTracking = false
        motorController.stop()
        targetTracker.clear()
        selectedTargetId = null
        Log.d("TargetTrackingService", "Tracking stopped")
    }

    fun selectTarget(objectId: Int) {
        selectedTargetId = objectId
        Log.d("TargetTrackingService", "Target selected: $objectId")
    }

    inner class LocalBinder : Binder() {
        fun getService(): TargetTrackingService = this@TargetTrackingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        objectDetector.release()
        motorController.disconnect()
        scope.cancel()
        Log.d("TargetTrackingService", "Service destroyed")
    }

    companion object {
        const val TAG = "TargetTrackingService"
    }
}