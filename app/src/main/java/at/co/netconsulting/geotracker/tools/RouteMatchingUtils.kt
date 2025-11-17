package at.co.netconsulting.geotracker.tools

import at.co.netconsulting.geotracker.data.EventWithDetails
import at.co.netconsulting.geotracker.data.GeoLocation
import at.co.netconsulting.geotracker.data.RouteComparison
import at.co.netconsulting.geotracker.data.RouteSimilarity
import at.co.netconsulting.geotracker.data.SegmentComparison
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Utility class for route matching and comparison
 */
object RouteMatchingUtils {

    private const val START_END_PROXIMITY_THRESHOLD = 200.0 // meters (increased from 100m)
    private const val DISTANCE_RATIO_THRESHOLD = 0.3 // ±30% (increased from ±20%)
    private const val MIN_SIMILARITY_SCORE = 0.3 // Minimum score to consider routes similar (30% match required)
    private const val POINT_MATCH_THRESHOLD = 30.0 // meters - points within this distance are considered matching

    /**
     * Calculate similarity between a reference route and a list of candidate routes
     * Returns list of similar routes sorted by similarity score (highest first)
     */
    fun findSimilarRoutes(
        referenceLocations: List<Location>,
        referenceTotalDistance: Double,
        candidateRoutes: List<Pair<Int, List<Location>>>, // eventId to locations
        candidateDistances: Map<Int, Double>
    ): List<RouteSimilarity> {
        if (referenceLocations.isEmpty()) return emptyList()

        val referenceStart = referenceLocations.first().toGeoLocation()
        val referenceEnd = referenceLocations.last().toGeoLocation()

        val similarities = candidateRoutes.mapNotNull { (eventId, locations) ->
            if (locations.isEmpty()) return@mapNotNull null

            val candidateStart = locations.first().toGeoLocation()
            val candidateEnd = locations.last().toGeoLocation()
            val candidateDistance = candidateDistances[eventId] ?: 0.0

            // Calculate start and end point distances
            val startDistance = referenceStart.distanceTo(candidateStart)
            val endDistance = referenceEnd.distanceTo(candidateEnd)

            // Check if start/end points are close enough
            if (startDistance > START_END_PROXIMITY_THRESHOLD ||
                endDistance > START_END_PROXIMITY_THRESHOLD
            ) {
                android.util.Log.d("RouteMatching", "Route $eventId rejected: start=${startDistance.toInt()}m (max 200m), end=${endDistance.toInt()}m (max 200m)")
                return@mapNotNull null
            }

            // Calculate distance ratio
            val distanceRatio = if (referenceTotalDistance > 0) {
                candidateDistance / referenceTotalDistance
            } else 1.0

            // Check if distances are similar enough
            if (abs(distanceRatio - 1.0) > DISTANCE_RATIO_THRESHOLD) {
                android.util.Log.d("RouteMatching", "Route $eventId rejected: distance ratio=${String.format("%.2f", distanceRatio)} (must be 0.7-1.3)")
                return@mapNotNull null
            }

            // Calculate path similarity using simplified paths
            val pathSimilarity = calculatePathSimilarity(referenceLocations, locations)

            // Calculate overall similarity score (weighted average)
            val startEndScore = 1.0 - (min(startDistance, 100.0) / 100.0)
            val distanceScore = 1.0 - (abs(distanceRatio - 1.0) / DISTANCE_RATIO_THRESHOLD)
            val similarityScore = (startEndScore * 0.3 + distanceScore * 0.2 + pathSimilarity * 0.5)

            if (similarityScore < MIN_SIMILARITY_SCORE) {
                android.util.Log.d("RouteMatching", "Route $eventId rejected: similarity score=${String.format("%.2f", similarityScore)} (min 0.5)")
                return@mapNotNull null
            }

            android.util.Log.d("RouteMatching", "Route $eventId MATCHED! score=${String.format("%.2f", similarityScore)}, start=${startDistance.toInt()}m, end=${endDistance.toInt()}m")

            RouteSimilarity(
                eventId = eventId,
                eventName = "", // Will be filled by caller
                eventDate = "", // Will be filled by caller
                similarityScore = similarityScore,
                startPointDistance = startDistance,
                endPointDistance = endDistance,
                distanceRatio = distanceRatio,
                pathSimilarity = pathSimilarity
            )
        }

        return similarities.sortedByDescending { it.similarityScore }
    }

    /**
     * Calculate path similarity between two routes by comparing all points
     * Returns a score from 0.0 (completely different) to 1.0 (identical)
     *
     * Algorithm:
     * - Compares ALL points from both routes (no simplification)
     * - A point matches if there's a point in the other route within 30 meters
     * - Returns the percentage of matching points (0.0 to 1.0)
     * - Requires ~90% match (MIN_SIMILARITY_SCORE) for routes to be considered similar
     */
    private fun calculatePathSimilarity(
        route1: List<Location>,
        route2: List<Location>
    ): Double {
        if (route1.isEmpty() || route2.isEmpty()) return 0.0

        // Convert to GeoLocation for distance calculations
        val geoRoute1 = route1.map { it.toGeoLocation() }
        val geoRoute2 = route2.map { it.toGeoLocation() }

        // Count how many points in route1 have a matching point in route2 (within 30m)
        val matchingPointsFromRoute1 = geoRoute1.count { p1 ->
            geoRoute2.any { p2 -> p1.distanceTo(p2) <= POINT_MATCH_THRESHOLD }
        }

        // Count how many points in route2 have a matching point in route1 (within 30m)
        val matchingPointsFromRoute2 = geoRoute2.count { p2 ->
            geoRoute1.any { p1 -> p1.distanceTo(p2) <= POINT_MATCH_THRESHOLD }
        }

        // Calculate bidirectional match percentage
        val matchPercentage1 = matchingPointsFromRoute1.toDouble() / geoRoute1.size
        val matchPercentage2 = matchingPointsFromRoute2.toDouble() / geoRoute2.size

        // Use the average of both directions for the final score
        val similarityScore = (matchPercentage1 + matchPercentage2) / 2.0

        android.util.Log.d("RouteMatching",
            "Point matching: route1=${geoRoute1.size} points (${matchingPointsFromRoute1} matched, ${String.format("%.1f", matchPercentage1 * 100)}%), " +
            "route2=${geoRoute2.size} points (${matchingPointsFromRoute2} matched, ${String.format("%.1f", matchPercentage2 * 100)}%), " +
            "final score=${String.format("%.3f", similarityScore)}")

        return similarityScore
    }

    /**
     * Simplify a route by selecting evenly distributed points
     */
    private fun simplifyRoute(locations: List<Location>, targetPoints: Int): List<GeoLocation> {
        if (locations.size <= targetPoints) {
            return locations.map { it.toGeoLocation() }
        }

        val step = locations.size.toDouble() / targetPoints
        return (0 until targetPoints).map { i ->
            val index = (i * step).toInt().coerceIn(0, locations.size - 1)
            locations[index].toGeoLocation()
        }
    }

    /**
     * Create a detailed comparison between two routes
     */
    fun compareRoutes(
        primaryEvent: EventWithDetails,
        comparisonEvent: EventWithDetails,
        similarityScore: Double,
        primaryLocations: List<Location>,
        comparisonLocations: List<Location>,
        primaryMetrics: List<Metric>,
        comparisonMetrics: List<Metric>
    ): RouteComparison {
        // Calculate time difference
        val primaryDuration = primaryEvent.endTime - primaryEvent.startTime
        val comparisonDuration = comparisonEvent.endTime - comparisonEvent.startTime
        val timeDifference = comparisonDuration - primaryDuration

        // Calculate speed differences
        val avgSpeedDiff = comparisonEvent.averageSpeed - primaryEvent.averageSpeed
        val maxSpeedDiff = (comparisonMetrics.maxOfOrNull { it.speed }?.toDouble() ?: 0.0) -
                (primaryMetrics.maxOfOrNull { it.speed }?.toDouble() ?: 0.0)

        // Calculate elevation differences
        val elevationGainDiff = comparisonEvent.elevationGain - primaryEvent.elevationGain
        val avgSlopeDiff = comparisonEvent.averageSlope - primaryEvent.averageSlope

        // Calculate heart rate differences
        val avgHrDiff = if (primaryEvent.avgHeartRate > 0 && comparisonEvent.avgHeartRate > 0) {
            comparisonEvent.avgHeartRate - primaryEvent.avgHeartRate
        } else null

        val maxHrDiff = if (primaryEvent.maxHeartRate > 0 && comparisonEvent.maxHeartRate > 0) {
            comparisonEvent.maxHeartRate - primaryEvent.maxHeartRate
        } else null

        val minHrDiff = if (primaryEvent.minHeartRate > 0 && comparisonEvent.minHeartRate > 0) {
            comparisonEvent.minHeartRate - primaryEvent.minHeartRate
        } else null

        // Calculate pacing consistency (lower standard deviation = better pacing)
        val primarySpeedStdDev = calculateSpeedStdDev(primaryMetrics)
        val comparisonSpeedStdDev = calculateSpeedStdDev(comparisonMetrics)
        val hasBetterPacing = comparisonSpeedStdDev < primarySpeedStdDev

        // Create segment comparisons (divide route into 10 segments)
        val segmentComparisons = createSegmentComparisons(
            primaryMetrics,
            comparisonMetrics,
            primaryEvent.totalDistance,
            numSegments = 10
        )

        return RouteComparison(
            primaryEvent = primaryEvent,
            comparisonEvent = comparisonEvent,
            similarityScore = similarityScore,
            timeDifference = timeDifference,
            distanceDifference = comparisonEvent.totalDistance - primaryEvent.totalDistance,
            avgSpeedDifference = avgSpeedDiff,
            maxSpeedDifference = maxSpeedDiff,
            elevationGainDifference = elevationGainDiff,
            avgSlopeDifference = avgSlopeDiff,
            avgHeartRateDifference = avgHrDiff,
            maxHeartRateDifference = maxHrDiff,
            minHeartRateDifference = minHrDiff,
            primaryAvgHeartRate = primaryEvent.avgHeartRate,
            primaryMaxHeartRate = primaryEvent.maxHeartRate,
            primaryMinHeartRate = primaryEvent.minHeartRate,
            comparisonAvgHeartRate = comparisonEvent.avgHeartRate,
            comparisonMaxHeartRate = comparisonEvent.maxHeartRate,
            comparisonMinHeartRate = comparisonEvent.minHeartRate,
            isFasterOverall = timeDifference < 0,
            hasBetterPacing = hasBetterPacing,
            hasLessElevationGain = elevationGainDiff < 0,
            segmentComparisons = segmentComparisons
        )
    }

    /**
     * Calculate standard deviation of speed for pacing analysis
     */
    private fun calculateSpeedStdDev(metrics: List<Metric>): Double {
        if (metrics.isEmpty()) return 0.0

        val speeds = metrics.map { it.speed.toDouble() }
        val mean = speeds.average()
        val variance = speeds.map { (it - mean) * (it - mean) }.average()
        return Math.sqrt(variance)
    }

    /**
     * Create segment-by-segment comparisons
     */
    private fun createSegmentComparisons(
        primaryMetrics: List<Metric>,
        comparisonMetrics: List<Metric>,
        totalDistance: Double,
        numSegments: Int
    ): List<SegmentComparison> {
        if (primaryMetrics.isEmpty() || comparisonMetrics.isEmpty() || totalDistance <= 0) {
            return emptyList()
        }

        val segmentDistance = totalDistance / numSegments
        val segments = mutableListOf<SegmentComparison>()

        for (i in 0 until numSegments) {
            val startDist = i * segmentDistance
            val endDist = (i + 1) * segmentDistance

            // Get metrics within this segment
            val primarySegmentMetrics = primaryMetrics.filter {
                it.distance in startDist..endDist
            }
            val comparisonSegmentMetrics = comparisonMetrics.filter {
                it.distance in startDist..endDist
            }

            if (primarySegmentMetrics.isEmpty() || comparisonSegmentMetrics.isEmpty()) continue

            // Calculate time for each segment
            val primarySegmentTime = if (primarySegmentMetrics.size > 1) {
                primarySegmentMetrics.last().timeInMilliseconds -
                        primarySegmentMetrics.first().timeInMilliseconds
            } else 0L

            val comparisonSegmentTime = if (comparisonSegmentMetrics.size > 1) {
                comparisonSegmentMetrics.last().timeInMilliseconds -
                        comparisonSegmentMetrics.first().timeInMilliseconds
            } else 0L

            // Calculate average speeds
            val primaryAvgSpeed = primarySegmentMetrics.map { it.speed.toDouble() }.average()
            val comparisonAvgSpeed = comparisonSegmentMetrics.map { it.speed.toDouble() }.average()

            // Calculate average heart rates for segment (if available)
            val primaryHrValues = primarySegmentMetrics.filter { it.heartRate > 0 }.map { it.heartRate }
            val comparisonHrValues = comparisonSegmentMetrics.filter { it.heartRate > 0 }.map { it.heartRate }

            val primaryAvgHr = if (primaryHrValues.isNotEmpty()) primaryHrValues.average().toInt() else null
            val comparisonAvgHr = if (comparisonHrValues.isNotEmpty()) comparisonHrValues.average().toInt() else null
            val hrDiff = if (primaryAvgHr != null && comparisonAvgHr != null) {
                comparisonAvgHr - primaryAvgHr
            } else null

            segments.add(
                SegmentComparison(
                    segmentIndex = i,
                    startDistanceMeters = startDist,
                    endDistanceMeters = endDist,
                    primaryTime = primarySegmentTime,
                    comparisonTime = comparisonSegmentTime,
                    timeDifference = comparisonSegmentTime - primarySegmentTime,
                    primaryAvgSpeed = primaryAvgSpeed,
                    comparisonAvgSpeed = comparisonAvgSpeed,
                    speedDifference = comparisonAvgSpeed - primaryAvgSpeed,
                    primaryAvgHeartRate = primaryAvgHr,
                    comparisonAvgHeartRate = comparisonAvgHr,
                    heartRateDifference = hrDiff
                )
            )
        }

        return segments
    }

    /**
     * Extension function to convert Location to GeoLocation
     */
    private fun Location.toGeoLocation(): GeoLocation {
        return GeoLocation(latitude, longitude, altitude)
    }
}
