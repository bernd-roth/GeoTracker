package at.co.netconsulting.geotracker.data

data class EventTimeRange(
    val eventId: Int,
    val minTime: Long,
    val maxTime: Long
)
