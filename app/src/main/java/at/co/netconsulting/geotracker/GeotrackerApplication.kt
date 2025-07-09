package at.co.netconsulting.geotracker

import android.app.Application
import timber.log.Timber

class GeoTrackerApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val isDebug = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

        if (isDebug) {
            // Plant a debug tree that shows logs in Logcat during development
            Timber.plant(Timber.DebugTree())
        } else {
            // Plant a custom tree for release builds (optional)
            // You can implement crash reporting here (Firebase Crashlytics, etc.)
            Timber.plant(CrashReportingTree())
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