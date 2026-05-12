package com.example.cameralink.tracking

import android.graphics.RectF

data class DetectedObject(
    val id: Int,
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val timestamp: Long = System.currentTimeMillis()
)