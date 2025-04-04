package at.co.netconsulting.geotracker.data

data class EventWithTotalDistance(
    val eventId: Int,
    val eventName: String,
    val artOfSport: String,
    val eventDate: String,
    val totalDistance: Double
)