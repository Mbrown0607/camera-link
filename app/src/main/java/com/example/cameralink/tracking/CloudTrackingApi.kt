package com.example.cameralink.tracking

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Multipart
import retrofit2.http.Part
import java.io.File
import java.io.FileOutputStream

// Data models
data class CloudDetectionRequest(
    @SerializedName("frame_base64")
    val frameBase64: String,
    @SerializedName("timestamp")
    val timestamp: Long
)

data class DetectionResult(
    @SerializedName("detections")
    val detections: List<CloudDetection>,
    @SerializedName("processing_time_ms")
    val processingTimeMs: Long
)

data class CloudDetection(
    @SerializedName("label")
    val label: String,
    @SerializedName("confidence")
    val confidence: Float,
    @SerializedName("bbox")
    val bbox: List<Float>  // [x1, y1, x2, y2]
)

// Retrofit API interface
interface CloudTrackingService {
    @POST("detect")
    suspend fun detectObjects(
        @Body request: CloudDetectionRequest
    ): DetectionResult

    @Multipart
    @POST("detect-frame")
    suspend fun detectObjectsFromFrame(
        @Part frame: MultipartBody.Part
    ): DetectionResult
}

/**
 * Cloud-based object detection using remote API
 * Supports async processing while local detection runs in real-time
 */
class CloudTrackingApi(
    private val baseUrl: String = "http://your-tracking-api-server.com/api/v1/"
) {
    private val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .client(
            OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
        )
        .build()

    private val service = retrofit.create(CloudTrackingService::class.java)

    /**
     * Send frame to cloud for detection (async)
     */
    suspend fun detectFromCloud(bitmap: Bitmap): Result<DetectionResult> = withContext(Dispatchers.IO) {
        try {
            val file = bitmapToFile(bitmap)
            val requestBody = file.asRequestBody("image/jpeg".toMediaType())
            val part = MultipartBody.Part.createFormData("frame", file.name, requestBody)

            val result = service.detectObjectsFromFrame(part)
            Log.d("CloudTracking", "Cloud detection completed in ${result.processingTimeMs}ms")
            Result.success(result)
        } catch (e: Exception) {
            Log.e("CloudTracking", "Cloud detection error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun bitmapToFile(bitmap: Bitmap): File {
        val file = File.createTempFile("frame", ".jpg")
        val fos = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
        fos.close()
        return file
    }
}