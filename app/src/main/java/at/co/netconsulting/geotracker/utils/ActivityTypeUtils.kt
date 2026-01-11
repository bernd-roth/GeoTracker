package at.co.netconsulting.geotracker.utils

/**
 * Utility class for determining activity type characteristics
 */
object ActivityTypeUtils {

    /**
     * Define stationary/indoor activities that don't require GPS tracking
     * These activities are typically performed indoors or in a fixed location
     */
    private val STATIONARY_ACTIVITIES = setOf(
        // Weight Training (new)
        "Weight Training",

        // Swimming
        "Swimming - Pool",

        // Indoor Ball Sports
        "Squash",
        "Table Tennis",
        "Basketball",
        "Volleyball",
        "Baseball",
        "Badminton",
        "Soccer",
        "American Football",
        "Fistball",
        "Tennis",

        // Ice Rink Sports
        "Ice Skating",
        "Ice Hockey"
    )

    /**
     * Check if an activity type is stationary/indoor
     * @param sportType The sport type string from artOfSport field
     * @return true if the activity is stationary/indoor
     */
    fun isStationaryActivity(sportType: String): Boolean {
        return STATIONARY_ACTIVITIES.contains(sportType)
    }

    /**
     * Check if GPS tracking should be enabled for this activity
     * @param sportType The sport type string
     * @return true if GPS should be enabled, false otherwise
     */
    fun requiresGpsTracking(sportType: String): Boolean {
        return !isStationaryActivity(sportType)
    }

    /**
     * Check if movement-based time tracking should be used
     * @param sportType The sport type string
     * @return true if movement-based time tracking should be used
     */
    fun usesMovementBasedTime(sportType: String): Boolean {
        return !isStationaryActivity(sportType)
    }
}
