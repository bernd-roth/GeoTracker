package at.co.netconsulting.geotracker.data

data class PathPoint(
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val distance: Double,
    val timestamp: Long,
    // Additional fields for enhanced popup
    val altitude: Double = 0.0,
    val totalDuration: Long = 0L, // Total duration from start in milliseconds
    val temperature: Float? = null,
    val windSpeed: Double? = null,
    val windDirection: Double? = null,
    val relativeHumidity: Int? = null,
    val pressure: Float? = null
)