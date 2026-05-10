package at.co.netconsulting.geotracker.data

/**
 * Event posted by ForegroundService each second while a "Wings for Life Run"
 * recording is active. Carries the catcher car's cumulative distance and the
 * runner's distance so subscribers (MapScreen) can interpolate the catcher's
 * GPS position along the recorded path and show it as a marker.
 */
data class WingsForLifeUpdate(
    val catcherDistanceMeters: Double,
    val runnerDistanceMeters: Double,
    val wasCaught: Boolean,
    val caughtAtDistanceMeters: Double,
    val caughtAtElapsedMs: Long,
    val elapsedMs: Long
)
