package at.co.netconsulting.geotracker.data

import java.time.LocalDateTime

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
    val firstname: String = "",
    val lastname: String = "",
    val birthdate: String = "",
    val height: Float = 0f,
    val weight: Float = 0f,

    // Settings fields
    val minDistanceMeters: Int = 0,
    val minTimeSeconds: Int = 0,
    val voiceAnnouncementInterval: Int = 0,

    // Event/Session information fields
    val eventName: String = "",
    val sportType: String = "",
    val comment: String = "",
    val clothing: String = "",

    // Heart rate data
    val heartRate: Int = 0,
    val heartRateDevice: String = "",

    // Weather data
    val temperature: Double = 0.0,
    val windSpeed: Double = 0.0,
    val windDirection: Double = 0.0,
    val relativeHumidity: Int = 0,
    val weatherCode: Int = 0,
    val weatherTime: String = "",

    // Barometer sensor data
    val pressure: Float = 0f,
    val pressureAccuracy: Int = 0,
    val altitudeFromPressure: Float = 0f,
    val seaLevelPressure: Float = 1013.25f,         // Standard atmospheric pressure

    // Fields for BottomSheet UI
    val numberOfSatellites: Int = 0,
    val usedNumberOfSatellites: Int = 0,

    // Fields derived from existing properties
    val distance: Double = coveredDistance,
    val currentSpeed: Double = speed.toDouble(),
    val movingAverageSpeed: Double,

    // Keep person for backward compatibility
    val person: String = firstname,

    // Satellites
    val satellites: Int? = null
)