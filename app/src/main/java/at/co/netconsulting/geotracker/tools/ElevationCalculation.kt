package at.co.netconsulting.geotracker.tools

import android.location.Location
import android.util.Log
import at.co.netconsulting.geotracker.SportType
import kotlin.math.abs

//ascending-descending
private var currentSportType = SportType.RUNNING // Should be configurable
private val CYCLING_HIGH_SPEED_THRESHOLD = 16.67 // 60 km/h in m/s
private val STEEP_GRADIENT_THRESHOLD = 0.35 // 35% gradient
private var altitudeBuffer = ArrayDeque<Double>(5) // Keep last 5 altitude readings
private val MIN_VERTICAL_ACCURACY = 8.0 // Maximum acceptable vertical accuracy error in meters
private var oldAscending: Double = 0.0
private var oldDescending: Double = 0.0
private var resultAscending: Double = 0.0
private var resultDescending: Double = 0.0

//ascending-descending
fun calculateElevationChanges(location: Location, oldLatitude: Double, oldLongitude: Double) {
    // Skip if accuracy is too poor
    if (location.hasVerticalAccuracy() && location.verticalAccuracyMeters > MIN_VERTICAL_ACCURACY) {
        Log.d("CustomLocationListener", "Skipping altitude calculation - poor vertical accuracy: ${location.verticalAccuracyMeters}m")
        return
    }

    // Add new altitude to buffer
    altitudeBuffer.addLast(location.altitude)
    if (altitudeBuffer.size > 5) {
        altitudeBuffer.removeFirst()
    }

    // Only proceed if we have enough readings
    if (altitudeBuffer.size < 3) {
        return
    }

    // Calculate smoothed altitude using moving average
    val smoothedAltitude = altitudeBuffer.average()

    // Adjust threshold based on sport type and conditions
    val adjustedThreshold = calculateDynamicThreshold(location, smoothedAltitude, oldLatitude, oldLongitude)

    // Calculate elevation changes with dynamic threshold
    calculateElevationWithDynamicThreshold(smoothedAltitude, adjustedThreshold)

    // Update old values
    oldAscending = smoothedAltitude
    oldDescending = smoothedAltitude
}

fun calculateDynamicThreshold(
    location: Location,
    currentAltitude: Double,
    oldLatitude: Double,
    oldLongitude: Double
): Double {
    // Base threshold depends on sport type
    val baseThreshold = when(currentSportType) {
        SportType.RUNNING -> 0.5  // 0.5m for running
        SportType.CYCLING -> 1.0  // 1m for normal cycling
        SportType.HIKING -> 0.3   // 0.3m for hiking (more precise)
    }

    // Calculate current gradient if we have previous readings
    val gradient = if (oldAscending != 0.0 && oldLatitude != -999.0 && oldLongitude != -999.0) {
        val horizontalDistance = calculateDistanceBetweenOldLatLngNewLatLng(
            oldLatitude, oldLongitude, location.latitude, location.longitude
        )
        val verticalDistance = abs(currentAltitude - oldAscending)
        if (horizontalDistance > 0) verticalDistance / horizontalDistance else 0.0
    } else 0.0

    // Adjust threshold based on speed and gradient
    var adjustedThreshold = baseThreshold

    when(currentSportType) {
        SportType.CYCLING -> {
            // For high-speed cycling, especially downhill
            if (location.speed > CYCLING_HIGH_SPEED_THRESHOLD && gradient > STEEP_GRADIENT_THRESHOLD) {
                // Increase threshold for high-speed descents
                adjustedThreshold = baseThreshold * (location.speed / CYCLING_HIGH_SPEED_THRESHOLD) * 1.5
                Log.d("CustomLocationListener", "High-speed descent detected, adjusted threshold: $adjustedThreshold")
            }
        }
        SportType.RUNNING -> {
            // Running adjustments if needed
            if (gradient > 0.15) { // Steep uphill running
                adjustedThreshold *= 1.2
            }
        }
        SportType.HIKING -> {
            // Hiking usually needs more precise measurements because we are moving slower
            if (location.speed < 1.0) { // Very slow movement
                adjustedThreshold *= 0.7 // More sensitive for slow hiking
            }
        }
    }
    return adjustedThreshold
}

private fun calculateDistanceBetweenOldLatLngNewLatLng(
    oldLatitude: Double,
    oldLongitude: Double,
    newLatitude: Double,
    newLongitude: Double
): Double {
    val result = FloatArray(1)
    Location.distanceBetween(
        oldLatitude,
        oldLongitude,
        newLatitude,
        newLongitude,
        result
    )
    return result[0].toDouble()
}

fun calculateElevationWithDynamicThreshold(smoothedAltitude: Double, threshold: Double) {
    if (oldAscending != 0.0) {
        val altitudeDifference = smoothedAltitude - oldAscending

        when {
            altitudeDifference > threshold -> {
                resultAscending += altitudeDifference
                Log.d("CustomLocationListener",
                    "Ascending: +${String.format("%.1f", altitudeDifference)}m " +
                            "(Total: ${String.format("%.1f", resultAscending)}m) " +
                            "[Threshold: ${String.format("%.1f", threshold)}m]")
            }
            altitudeDifference < -threshold -> {
                resultDescending += abs(altitudeDifference)
                Log.d("CustomLocationListener",
                    "Descending: +${String.format("%.1f", abs(altitudeDifference))}m " +
                            "(Total: ${String.format("%.1f", resultDescending)}m) " +
                            "[Threshold: ${String.format("%.1f", threshold)}m]")
            }
        }
    }
}
fun getTotalAscent(): Double {
    return resultAscending
}

fun getTotalDescent(): Double {
    return resultDescending
}

fun setSportType(sportType: SportType) {
    currentSportType = sportType
    // Reset buffers when changing sport type
    altitudeBuffer.clear()
    oldAscending = 0.0
    oldDescending = 0.0
}