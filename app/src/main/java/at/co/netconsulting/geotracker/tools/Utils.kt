package at.co.netconsulting.geotracker.tools

import android.app.ActivityManager
import android.content.Context

/**
 * Checks if a service is currently running
 * @param context Context to use for service manager
 * @param serviceName fully qualified class name of the service
 * @return true if the service is running, false otherwise
 */
fun isServiceRunning(context: Context, serviceName: String): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Integer.MAX_VALUE)
        .any { service ->
            serviceName == service.service.className && service.foreground
        }
}

/**
 * Gets the currently recording event ID from SharedPreferences
 * @param context Context to use for SharedPreferences
 * @return the active event ID or -1 if none is active
 */
fun getCurrentlyRecordingEventId(context: Context): Int {
    val sharedPreferences = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
    return if (isServiceRunning(context, "at.co.netconsulting.geotracker.service.ForegroundService")) {
        sharedPreferences.getInt("active_event_id", -1)
    } else {
        // Return the last recorded event ID when not recording
        sharedPreferences.getInt("last_event_id", -1)
    }
}