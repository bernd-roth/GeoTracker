package at.co.netconsulting.geotracker.data

/**
 * Data class representing a comparison between two route attempts
 */
data class RouteComparison(
    // The two events being compared
    val primaryEvent: EventWithDetails,
    val comparisonEvent: EventWithDetails,

    // Similarity score (0.0 to 1.0, where 1.0 is identical routes)
    val similarityScore: Double,

    // Performance differences
    val timeDifference: Long, // in milliseconds (negative = faster in comparison)
    val distanceDifference: Double, // in meters
    val avgSpeedDifference: Double, // in km/h
    val maxSpeedDifference: Double, // in km/h

    // Elevation differences
    val elevationGainDifference: Double, // in meters
    val avgSlopeDifference: Double, // in percentage

    // Heart rate differences (null if not available)
    val avgHeartRateDifference: Int? = null,
    val maxHeartRateDifference: Int? = null,
    val minHeartRateDifference: Int? = null,

    // Heart rate values for display
    val primaryAvgHeartRate: Int = 0,
    val primaryMaxHeartRate: Int = 0,
    val primaryMinHeartRate: Int = 0,
    val comparisonAvgHeartRate: Int = 0,
    val comparisonMaxHeartRate: Int = 0,
    val comparisonMinHeartRate: Int = 0,

    // Improvement indicators
    val isFasterOverall: Boolean,
    val hasBetterPacing: Boolean, // More consistent speed
    val hasLessElevationGain: Boolean,

    // Segment-by-segment comparison (optional for detailed analysis)
    val segmentComparisons: List<SegmentComparison> = emptyList()
)

/**
 * Comparison of a specific segment of the route
 */
data class SegmentComparison(
    val segmentIndex: Int,
    val startDistanceMeters: Double,
    val endDistanceMeters: Double,
    val primaryTime: Long, // milliseconds
    val comparisonTime: Long, // milliseconds
    val timeDifference: Long, // negative = faster in comparison
    val primaryAvgSpeed: Double, // km/h
    val comparisonAvgSpeed: Double, // km/h
    val speedDifference: Double,

    // Heart rate data for segment (null if not available)
    val primaryAvgHeartRate: Int? = null,
    val comparisonAvgHeartRate: Int? = null,
    val heartRateDifference: Int? = null
)

/**
 * Data class for route similarity calculation
 */
data class RouteSimilarity(
    val eventId: Int,
    val eventName: String,
    val eventDate: String,
    val similarityScore: Double,
    val startPointDistance: Double, // meters from reference start
    val endPointDistance: Double, // meters from reference end
    val distanceRatio: Double, // comparison distance / reference distance
    val pathSimilarity: Double // 0.0 to 1.0
)

/**
 * Simple data class for geographic points used in similarity calculation
 */
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0
) {
    /**
     * Calculate distance to another point using Haversine formula
     * Returns distance in meters
     */
    fun distanceTo(other: GeoLocation): Double {
        val earthRadius = 6371000.0 // meters

        val lat1Rad = Math.toRadians(latitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val deltaLatRad = Math.toRadians(other.latitude - latitude)
        val deltaLonRad = Math.toRadians(other.longitude - longitude)

        val a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }
}
