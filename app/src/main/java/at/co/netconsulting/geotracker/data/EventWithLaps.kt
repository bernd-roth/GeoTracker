package at.co.netconsulting.geotracker.data

data class EventWithLaps(
    val event: SingleEventWithMetric,
    val lapTimes: List<LapTimeInfo>
)