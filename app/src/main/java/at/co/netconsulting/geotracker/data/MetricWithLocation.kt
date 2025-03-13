package at.co.netconsulting.geotracker.data

data class MetricWithLocation(
    val metricId: Long,
    val eventId: Int,
    val heartRate: Int,
    val heartRateDevice: String,
    val speed: Float,
    val distance: Double,
    val cadence: Float?,
    val lap: Int,
    val timeInMilliseconds: Long,
    val unity: String?,
    val elevation: Float?,
    val elevationGain: Float?,
    val elevationLoss: Float?,
    val latitude: Double,
    val longitude: Double
)