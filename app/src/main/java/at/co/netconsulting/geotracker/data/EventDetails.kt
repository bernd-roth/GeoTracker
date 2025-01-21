package at.co.netconsulting.geotracker.data

data class EventDetails(
    val duration: String,
    val maxSpeed: Float,
    val avgSpeed: Float,
//    val totalAscent: Double,
//    val totalDescent: Double,
    val temperature: Float,
    val windSpeed: Float,
    val humidity: Int
)