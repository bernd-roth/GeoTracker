package at.co.netconsulting.geotracker.data

data class EventDetails(
    val duration: String,
    val maxSpeed: Float,
    val avgSpeed: Float,
    val windSpeed: Float,
    val humidity: Int,
    val maxTemperature: Float = 0f,
    val minTemperature: Float = 0f
)