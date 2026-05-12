package com.example.cameralink.tracking

import android.graphics.RectF

data class TrackingState(
    val isTracking: Boolean = false,
    val targetId: Int? = null,
    val targetBoundingBox: RectF? = null,
    val targetLabel: String? = null,
    val confidence: Float = 0f,
    val panAngle: Float = 0f,      // degrees for motorized control
    val tiltAngle: Float = 0f,     // degrees for motorized control
    val timestamp: Long = System.currentTimeMillis()
)