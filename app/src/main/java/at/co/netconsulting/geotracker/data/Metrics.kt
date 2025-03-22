package at.co.netconsulting.geotracker.data

import java.time.LocalDateTime

/**
 * Data class representing metrics collected during tracking
 * Enhanced to support both WebSocket server and UI components
 */
data class Metrics(
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val speedAccuracyMetersPerSecond: Float = 0f,
    val altitude: Double,
    val horizontalAccuracy: Float = 0f,
    val verticalAccuracyMeters: Float = 0f,
    val coveredDistance: Double = 0.0,
    val lap: Int = 0,
    val startDateTime: LocalDateTime,
    val averageSpeed: Double = 0.0,
    val maxSpeed: Double = 0.0,
    val cumulativeElevationGain: Double = 0.0,

    // WebSocket server fields
    val sessionId: String = "",
    val person: String = "",

    // Fields for BottomSheet UI
    val numberOfSatellites: Int = 0,
    val usedNumberOfSatellites: Int = 0,

    // Fields derived from existing properties (for WebSocket compatibility)
    val distance: Double = coveredDistance,
    val currentSpeed: Double = speed.toDouble(),
    val movingAverageSpeed: Double = averageSpeed
)