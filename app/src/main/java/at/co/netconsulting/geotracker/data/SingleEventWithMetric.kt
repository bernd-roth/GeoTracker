package at.co.netconsulting.geotracker.data

data class SingleEventWithMetric(
    val eventId: Int,
    val eventName: String,
    val eventDate: String,
    val artOfSport: String,
    val comment: String,
    val metricId: Int,
    val heartRate: Int,
    val heartRateDevice: String,
    val speed: Float,
    val distance: Double,
    val cadence: Int?,
    val lap: Int,
    val timeInMilliseconds: Long,
    val unity: String
)