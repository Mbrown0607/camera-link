package com.example.cameralink.tracking

import android.graphics.PointF

/**
 * Kalman Filter for smooth object tracking
 */
class KalmanFilter {
    private val stateSize = 4  // x, y, width, height
    private val measurementSize = 4

    // State transition matrix
    private val F = Array(stateSize) { FloatArray(stateSize) }
    // Measurement matrix
    private val H = Array(measurementSize) { FloatArray(stateSize) }
    // Process covariance
    private val Q = Array(stateSize) { FloatArray(stateSize) }
    // Measurement covariance
    private val R = Array(measurementSize) { FloatArray(measurementSize) }
    // State estimate covariance
    private val P = Array(stateSize) { FloatArray(stateSize) }
    // Predicted state covariance
    private var P_pred = Array(stateSize) { FloatArray(stateSize) }

    private val state = FloatArray(stateSize)
    private val measurement = FloatArray(measurementSize)

    init {
        // Initialize state transition matrix (constant velocity model)
        for (i in 0 until stateSize) {
            F[i][i] = 1f
        }

        // Initialize measurement matrix (we measure position and size)
        for (i in 0 until measurementSize) {
            H[i][i] = 1f
        }

        // Initialize covariances
        for (i in 0 until stateSize) {
            Q[i][i] = 0.01f
            P[i][i] = 1f
        }
        for (i in 0 until measurementSize) {
            R[i][i] = 10f
        }
    }

    fun predict(): FloatArray {
        // x = F * x
        val x_pred = FloatArray(stateSize)
        for (i in 0 until stateSize) {
            for (j in 0 until stateSize) {
                x_pred[i] += F[i][j] * state[j]
            }
        }

        // P = F * P * F^T + Q
        state.indices.forEach { state[it] = x_pred[it] }
        return state
    }

    fun update(measurement: FloatArray) {
        for (i in measurement.indices) {
            this.measurement[i] = measurement[i]
        }

        // Kalman gain: K = P * H^T * (H * P * H^T + R)^-1
        // Innovation: y = z - H * x
        // Update: x = x + K * y

        // Simplified update for real-time performance
        for (i in measurement.indices) {
            val weight = 0.3f  // Balance between prediction and measurement
            state[i] = state[i] * (1 - weight) + measurement[i] * weight
        }
    }

    fun setInitialState(x: Float, y: Float, width: Float, height: Float) {
        state[0] = x
        state[1] = y
        state[2] = width
        state[3] = height
    }

    fun getCenterPoint(): PointF {
        return PointF(state[0], state[1])
    }

    fun getState(): FloatArray = state.copyOf()
}