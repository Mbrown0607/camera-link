package com.example.cameralink.tracking

data class MotorControlCommand(
    val panAngle: Float,      // -180 to 180 degrees
    val tiltAngle: Float,     // -90 to 90 degrees
    val speed: Int = 50,      // 0-100 percent
    val timestamp: Long = System.currentTimeMillis()
)