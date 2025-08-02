package at.co.netconsulting.geotracker.tools

import at.co.netconsulting.geotracker.data.GpsStatus
import at.co.netconsulting.geotracker.enums.GpsFixStatus

object GpsStatusEvaluator {

    // Thresholds for good GPS fix
    private const val MIN_HORIZONTAL_ACCURACY = 15.0f // meters
    private const val MIN_VERTICAL_ACCURACY = 25.0f   // meters
    private const val MIN_SPEED_ACCURACY = 2.0f       // m/s
    private const val MIN_SATELLITES = 4
    private const val MIN_USED_SATELLITES = 3

    fun evaluateGpsStatus(
        latitude: Double,
        longitude: Double,
        horizontalAccuracy: Float,
        verticalAccuracy: Float,
        speedAccuracy: Float,
        satelliteCount: Int,
        usedSatelliteCount: Int
    ): GpsStatus {

        // Check if location is available
        if (latitude == -999.0 || longitude == -999.0 ||
            latitude.isNaN() || longitude.isNaN()) {
            return GpsStatus(
                status = GpsFixStatus.NO_LOCATION,
                message = "Waiting for GPS location...",
                isReadyToRecord = false,
                horizontalAccuracy = horizontalAccuracy,
                verticalAccuracy = verticalAccuracy,
                speedAccuracy = speedAccuracy,
                satelliteCount = satelliteCount,
                usedSatelliteCount = usedSatelliteCount
            )
        }

        // Check satellite count
        if (usedSatelliteCount < MIN_USED_SATELLITES || satelliteCount < MIN_SATELLITES) {
            return GpsStatus(
                status = GpsFixStatus.INSUFFICIENT_SATELLITES,
                message = "Waiting for more satellites ($usedSatelliteCount/$satelliteCount visible, need $MIN_USED_SATELLITES+)",
                isReadyToRecord = false,
                horizontalAccuracy = horizontalAccuracy,
                verticalAccuracy = verticalAccuracy,
                speedAccuracy = speedAccuracy,
                satelliteCount = satelliteCount,
                usedSatelliteCount = usedSatelliteCount
            )
        }

        // Check horizontal accuracy
        if (horizontalAccuracy > MIN_HORIZONTAL_ACCURACY) {
            return GpsStatus(
                status = GpsFixStatus.POOR_ACCURACY,
                message = "GPS accuracy too low (±${String.format("%.1f", horizontalAccuracy)}m, need ±${MIN_HORIZONTAL_ACCURACY}m or better)",
                isReadyToRecord = false,
                horizontalAccuracy = horizontalAccuracy,
                verticalAccuracy = verticalAccuracy,
                speedAccuracy = speedAccuracy,
                satelliteCount = satelliteCount,
                usedSatelliteCount = usedSatelliteCount
            )
        }

        // Check vertical accuracy (if available)
        if (verticalAccuracy > 0 && verticalAccuracy > MIN_VERTICAL_ACCURACY) {
            return GpsStatus(
                status = GpsFixStatus.POOR_ACCURACY,
                message = "GPS altitude accuracy too low (±${String.format("%.1f", verticalAccuracy)}m)",
                isReadyToRecord = false,
                horizontalAccuracy = horizontalAccuracy,
                verticalAccuracy = verticalAccuracy,
                speedAccuracy = speedAccuracy,
                satelliteCount = satelliteCount,
                usedSatelliteCount = usedSatelliteCount
            )
        }

        // GPS fix is good
        return GpsStatus(
            status = GpsFixStatus.GOOD_FIX,
            message = "GPS ready - Good signal quality",
            isReadyToRecord = true,
            horizontalAccuracy = horizontalAccuracy,
            verticalAccuracy = verticalAccuracy,
            speedAccuracy = speedAccuracy,
            satelliteCount = satelliteCount,
            usedSatelliteCount = usedSatelliteCount
        )
    }

    /**
     * Get a user-friendly status message with details
     */
    fun getDetailedStatusMessage(gpsStatus: GpsStatus): String {
        return when (gpsStatus.status) {
            GpsFixStatus.NO_LOCATION -> "🔍 Searching for GPS signal..."
            GpsFixStatus.INSUFFICIENT_SATELLITES -> "🛰️ ${gpsStatus.message}"
            GpsFixStatus.POOR_ACCURACY -> "📍 ${gpsStatus.message}"
            GpsFixStatus.GOOD_FIX -> "✅ ${gpsStatus.message}"
            else -> {}
        } as String
    }
}