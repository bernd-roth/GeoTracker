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