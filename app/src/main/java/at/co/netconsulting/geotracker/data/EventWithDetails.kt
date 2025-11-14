import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.domain.Weather
import org.osmdroid.util.GeoPoint

data class EventWithDetails(
    val event: Event,

    // Flag to indicate if full details have been loaded
    val hasFullDetails: Boolean = false,

    // Always loaded (minimal data)
    val locationPointCount: Int = 0, // Number of location points (for memory efficiency)

    // Lazy-loaded fields (only calculated when event card is expanded)
    val totalDistance: Double = 0.0,
    val averageSpeed: Double = 0.0,
    val maxElevation: Double = 0.0,
    val minElevation: Double = 0.0,
    val maxElevationGain: Double = 0.0,
    val elevationGain: Double = 0.0,
    val elevationLoss: Double = 0.0,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val weather: Weather? = null,
    val laps: List<Long> = emptyList(),
    val locationPoints: List<GeoPoint> = emptyList(),

    // min/max/avg number of satellites
    val minSatellites: Int = 0,
    val maxSatellites: Int = 0,
    val avgSatellites: Int = 0,

    // Heart rate related fields
    val minHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val avgHeartRate: Int = 0,
    val heartRateDevice: String = "",

    // Slope calculations
    val averageSlope: Double = 0.0,
    val maxSlope: Double = 0.0,
    val minSlope: Double = 0.0,

    // Added metrics for altitude graph
    val metrics: List<Metric> = emptyList()
)