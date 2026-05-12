package com.example.cameralink.tracking

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import kotlin.math.sqrt

/**
 * Tracks detected objects using Kalman filtering and association
 */
class TargetTracker {
    private val kalmanFilters = mutableMapOf<Int, KalmanFilter>()
    private val trackedObjects = mutableMapOf<Int, DetectedObject>()
    private val maxTrackingDistance = 100f  // pixels
    private var nextTrackId = 0

    /**
     * Update tracker with new detections
     */
    fun update(detections: List<DetectedObject>): Map<Int, DetectedObject> {
        if (detections.isEmpty()) {
            return trackedObjects
        }

        // Predict next positions
        kalmanFilters.forEach { (id, filter) ->
            filter.predict()
        }

        // Associate detections to tracked objects
        val associations = associateDetections(detections)

        // Update or create tracks
        associations.forEach { (detectionId, trackId) ->
            val detection = detections.find { it.id == detectionId } ?: return@forEach
            
            if (trackId != null) {
                // Update existing track
                kalmanFilters[trackId]?.let { filter ->
                    filter.update(floatArrayOf(
                        detection.boundingBox.centerX(),
                        detection.boundingBox.centerY(),
                        detection.boundingBox.width(),
                        detection.boundingBox.height()
                    ))
                }
                trackedObjects[trackId] = detection
            } else {
                // Create new track
                val newId = nextTrackId++
                val filter = KalmanFilter()
                filter.setInitialState(
                    detection.boundingBox.centerX(),
                    detection.boundingBox.centerY(),
                    detection.boundingBox.width(),
                    detection.boundingBox.height()
                )
                kalmanFilters[newId] = filter
                trackedObjects[newId] = detection
            }
        }

        // Remove lost tracks
        val validIds = associations.mapNotNull { it.second }.toSet()
        kalmanFilters.keys.retainAll(validIds)
        trackedObjects.keys.retainAll(validIds)

        return trackedObjects
    }

    /**
     * Associate detections to existing tracks using Hungarian algorithm approximation
     */
    private fun associateDetections(detections: List<DetectedObject>): List<Pair<Int, Int?>> {
        val associations = mutableListOf<Pair<Int, Int?>>()

        for (detection in detections) {
            var bestTrackId: Int? = null
            var bestDistance = maxTrackingDistance

            for ((trackId, filter) in kalmanFilters) {
                val predictedCenter = filter.getCenterPoint()
                val detectionCenter = PointF(
                    detection.boundingBox.centerX(),
                    detection.boundingBox.centerY()
                )

                val distance = distance(predictedCenter, detectionCenter)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestTrackId = trackId
                }
            }

            associations.add(Pair(detection.id, bestTrackId))
        }

        return associations
    }

    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    fun getTrackedObjects(): Map<Int, DetectedObject> = trackedObjects.toMap()

    fun clear() {
        kalmanFilters.clear()
        trackedObjects.clear()
    }
}