package at.co.netconsulting.geotracker.data

data class RecordingData(
    val eventDate: String,
    val eventName: String,
    val distance: Double,
    val speed: Double
)