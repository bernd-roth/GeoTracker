package at.co.netconsulting.geotracker

import android.app.Application
import android.util.Log
import at.co.netconsulting.geotracker.reminder.ReminderManager
import timber.log.Timber

class GeoTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val isDebug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

        if (isDebug) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(CrashReportingTree())
        }

        // Ensure alarms are scheduled when app starts
        // This is a safety net in case broadcasts were missed
        try {
            val reminderManager = ReminderManager(this)
            reminderManager.ensureAlarmsAreScheduled()
        } catch (e: Exception) {
            Log.e("GeoTrackerApplication", "Failed to ensure alarms on app start", e)
        }
    }
}

// Custom tree for release builds
class CrashReportingTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == android.util.Log.ERROR || priority == android.util.Log.WARN) {
            // Send to crash reporting service (e.g., Firebase Crashlytics)
            // FirebaseCrashlytics.getInstance().log(message)
            // if (t != null) FirebaseCrashlytics.getInstance().recordException(t)
        }
    }
}