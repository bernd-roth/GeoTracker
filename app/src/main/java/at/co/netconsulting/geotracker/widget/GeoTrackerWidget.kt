package at.co.netconsulting.geotracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import at.co.netconsulting.geotracker.MainActivity
import at.co.netconsulting.geotracker.R
import at.co.netconsulting.geotracker.data.FollowedUserPoint
import at.co.netconsulting.geotracker.service.WidgetFollowingService
import java.util.Locale

class GeoTrackerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, Context.MODE_PRIVATE)
        for (appWidgetId in appWidgetIds) {
            try {
                val configuredPerson = prefs.getString("widget_${appWidgetId}_person", null)
                if (configuredPerson != null) {
                    // Following widget - show waiting state with person name
                    val data = WidgetData(isFollowing = true, personName = configuredPerson)
                    updateAppWidget(context, appWidgetManager, appWidgetId, data)
                } else {
                    updateAppWidget(context, appWidgetManager, appWidgetId, WidgetData())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget $appWidgetId", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive: ${intent.action}")
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "Widget enabled (first instance added)")
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "Widget disabled (last instance removed)")
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Log.d(TAG, "onDeleted called for ${appWidgetIds.size} widgets")
        val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (id in appWidgetIds) {
            editor.remove("widget_${id}_session_id")
            editor.remove("widget_${id}_person")
        }
        editor.apply()

        // Check if any remaining widgets have following configured
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val remainingIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, GeoTrackerWidget::class.java)
        )
        val hasFollowingWidgets = remainingIds.any { id ->
            prefs.getString("widget_${id}_session_id", null) != null
        }
        if (!hasFollowingWidgets) {
            Log.d(TAG, "No more following widgets, stopping WidgetFollowingService")
            context.stopService(Intent(context, WidgetFollowingService::class.java))
        }
    }

    data class WidgetData(
        val totalActivity: String = "--:--:--",
        val duration: String = "--:--:--",
        val inactivity: String = "--:--:--",
        val distance: Double = 0.0,
        val speed: Float = 0.0f,
        val altitude: Double = 0.0,
        val temperature: Float = Float.MIN_VALUE,
        val barometer: Float = 0.0f,
        val heartRate: Int = 0,
        val isTracking: Boolean = false,
        val isFollowing: Boolean = false,
        val personName: String? = null
    )

    companion object {
        private const val TAG = "GeoTrackerWidget"
        const val WIDGET_CONFIG_PREFS = "WidgetConfig"

        /**
         * Update own-stats widgets only (widgets without following config).
         * Called by ForegroundService during recording.
         */
        fun updateWidget(
            context: Context,
            totalActivity: String,
            duration: String,
            inactivity: String,
            distance: Double,
            speed: Float,
            altitude: Double,
            temperature: Float?,
            barometer: Float,
            heartRate: Int,
            isTracking: Boolean
        ) {
            try {
                val data = WidgetData(
                    totalActivity = totalActivity,
                    duration = duration,
                    inactivity = inactivity,
                    distance = distance,
                    speed = speed,
                    altitude = altitude,
                    temperature = temperature ?: Float.MIN_VALUE,
                    barometer = barometer,
                    heartRate = heartRate,
                    isTracking = isTracking
                )

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, Context.MODE_PRIVATE)
                val allIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, GeoTrackerWidget::class.java)
                )

                for (id in allIds) {
                    // Only update widgets that are NOT configured for following
                    if (prefs.getString("widget_${id}_session_id", null) == null) {
                        updateAppWidget(context, appWidgetManager, id, data)
                    }
                }

                Log.d(TAG, "Own stats updated: distance=$distance, speed=$speed, tracking=$isTracking")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating own-stats widgets", e)
            }
        }

        /**
         * Reset own-stats widgets to not-tracking state.
         * Only affects widgets without following config.
         */
        fun updateWidgetNotTracking(context: Context) {
            try {
                val data = WidgetData()
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, Context.MODE_PRIVATE)
                val allIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, GeoTrackerWidget::class.java)
                )

                for (id in allIds) {
                    if (prefs.getString("widget_${id}_session_id", null) == null) {
                        updateAppWidget(context, appWidgetManager, id, data)
                    }
                }

                Log.d(TAG, "Own stats widgets reset to not-tracking")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting own-stats widgets", e)
            }
        }

        /**
         * Update following widgets with followed runner data.
         * Only affects widgets configured to follow the given session.
         */
        fun updateWidgetFollowing(
            context: Context,
            point: FollowedUserPoint,
            duration: String,
            temperature: Float?,
            barometer: Float?
        ) {
            try {
                val data = WidgetData(
                    totalActivity = duration,
                    duration = duration,
                    inactivity = "---",
                    distance = point.distance,
                    speed = point.currentSpeed,
                    altitude = point.altitude,
                    temperature = temperature ?: Float.MIN_VALUE,
                    barometer = barometer ?: 0.0f,
                    heartRate = point.heartRate ?: 0,
                    isTracking = true,
                    isFollowing = true,
                    personName = point.person
                )

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, Context.MODE_PRIVATE)
                val allIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, GeoTrackerWidget::class.java)
                )

                for (id in allIds) {
                    // Only update widgets configured to follow this session
                    val configuredSession = prefs.getString("widget_${id}_session_id", null)
                    if (configuredSession == point.sessionId) {
                        updateAppWidget(context, appWidgetManager, id, data)
                    }
                }

                Log.d(TAG, "Following widget updated for ${point.person}: distance=${point.distance}, speed=${point.currentSpeed}")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating following widgets", e)
            }
        }

        /**
         * Reset following widgets to not-tracking state.
         * Called when followed runner goes offline.
         */
        fun resetFollowingWidgets(context: Context, sessionId: String) {
            try {
                val data = WidgetData()
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val prefs = context.getSharedPreferences(WIDGET_CONFIG_PREFS, Context.MODE_PRIVATE)
                val allIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, GeoTrackerWidget::class.java)
                )

                for (id in allIds) {
                    val configuredSession = prefs.getString("widget_${id}_session_id", null)
                    if (configuredSession == sessionId) {
                        // Show person name with offline state
                        val personName = prefs.getString("widget_${id}_person", null)
                        val offlineData = data.copy(isFollowing = true, personName = personName)
                        updateAppWidget(context, appWidgetManager, id, offlineData)
                    }
                }

                Log.d(TAG, "Following widgets reset for session $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting following widgets", e)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            data: WidgetData
        ) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_geotracker)

                // Set click listener - open config activity when following, MainActivity otherwise
                val clickIntent = if (data.isFollowing) {
                    Intent(context, WidgetConfigActivity::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    }
                } else {
                    Intent(context, MainActivity::class.java)
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId, // unique requestCode per widget so each gets its own PendingIntent
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                // Update status text
                if (data.isFollowing && data.personName != null) {
                    views.setTextViewText(
                        R.id.widget_status,
                        context.getString(R.string.widget_following, data.personName)
                    )
                    views.setTextColor(R.id.widget_status, 0xFF2196F3.toInt()) // Blue
                } else if (data.isTracking) {
                    views.setTextViewText(R.id.widget_status, "Recording...")
                    views.setTextColor(R.id.widget_status, 0xFF4CAF50.toInt()) // Green
                } else {
                    views.setTextViewText(R.id.widget_status, "Not Recording")
                    views.setTextColor(R.id.widget_status, 0xFF808080.toInt()) // Gray
                }

                // Update values
                if (data.isTracking || (data.isFollowing && data.distance > 0)) {
                    // Total Activity
                    views.setTextViewText(R.id.widget_total_activity_value, data.totalActivity)

                    // Duration
                    views.setTextViewText(R.id.widget_duration_value, data.duration)

                    // Inactivity
                    views.setTextViewText(R.id.widget_inactivity_value, data.inactivity)

                    // Distance in km (input is in meters)
                    val distanceKm = data.distance / 1000.0
                    views.setTextViewText(
                        R.id.widget_distance_value,
                        String.format(Locale.getDefault(), "%.2f", distanceKm)
                    )

                    // Speed in km/h
                    val speedKmh = data.speed
                    views.setTextViewText(
                        R.id.widget_speed_value,
                        String.format(Locale.getDefault(), "%.1f", speedKmh)
                    )

                    // Altitude in meters
                    views.setTextViewText(
                        R.id.widget_altitude_value,
                        String.format(Locale.getDefault(), "%.0f", data.altitude)
                    )

                    // Temperature in Celsius
                    if (data.temperature != Float.MIN_VALUE) {
                        views.setTextViewText(
                            R.id.widget_temperature_value,
                            String.format(Locale.getDefault(), "%.1f", data.temperature)
                        )
                    } else {
                        views.setTextViewText(R.id.widget_temperature_value, "---")
                    }

                    // Barometer in hPa
                    if (data.barometer > 0) {
                        views.setTextViewText(
                            R.id.widget_barometer_value,
                            String.format(Locale.getDefault(), "%.0f", data.barometer)
                        )
                    } else {
                        views.setTextViewText(R.id.widget_barometer_value, "---")
                    }

                    // Heart rate in bpm
                    if (data.heartRate > 0) {
                        views.setTextViewText(
                            R.id.widget_heartrate_value,
                            data.heartRate.toString()
                        )
                    } else {
                        views.setTextViewText(R.id.widget_heartrate_value, "---")
                    }
                } else {
                    views.setTextViewText(R.id.widget_total_activity_value, "--:--:--")
                    views.setTextViewText(R.id.widget_duration_value, "--:--:--")
                    views.setTextViewText(R.id.widget_inactivity_value, "--:--:--")
                    views.setTextViewText(R.id.widget_distance_value, "---")
                    views.setTextViewText(R.id.widget_speed_value, "---")
                    views.setTextViewText(R.id.widget_altitude_value, "---")
                    views.setTextViewText(R.id.widget_temperature_value, "---")
                    views.setTextViewText(R.id.widget_barometer_value, "---")
                    views.setTextViewText(R.id.widget_heartrate_value, "---")
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
                Log.d(TAG, "Widget $appWidgetId updated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget $appWidgetId", e)
            }
        }
    }
}
