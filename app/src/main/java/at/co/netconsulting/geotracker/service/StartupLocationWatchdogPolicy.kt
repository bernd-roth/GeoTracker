package at.co.netconsulting.geotracker.service

/**
 * Detects the startup failure where GPS delivers one cached fix and then falls silent.
 * A healthy 1 Hz registration must produce repeated callbacks, not merely one fix.
 */
internal object StartupLocationWatchdogPolicy {
    const val REQUIRED_CALLBACKS = 2L

    fun shouldRestartInitially(callbackCount: Long): Boolean =
        callbackCount < REQUIRED_CALLBACKS

    fun shouldRetryAfterRestart(
        callbackCountAtRestart: Long,
        currentCallbackCount: Long
    ): Boolean =
        currentCallbackCount - callbackCountAtRestart < REQUIRED_CALLBACKS
}
