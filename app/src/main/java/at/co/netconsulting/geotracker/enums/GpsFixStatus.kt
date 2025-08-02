package at.co.netconsulting.geotracker.enums

enum class GpsFixStatus {
    NO_LOCATION,           // No GPS location available
    POOR_ACCURACY,         // GPS available but accuracy is poor
    INSUFFICIENT_SATELLITES, // Not enough satellites
    GOOD_FIX              // Good GPS fix, ready to record
}