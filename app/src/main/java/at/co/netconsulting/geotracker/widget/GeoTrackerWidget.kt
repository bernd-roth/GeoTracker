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
import java.util.Locale

class GeoTrackerWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")
        for (appWidgetId in appWidgetIds) {
            try {
                updateAppWidget(context, appWidgetManager, appWidgetId, WidgetData())
            } catch (e: Exception) {
                Log.e(TAG, "Error updating widget $appWidgetId", e)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        Log.d(TAG, "onReceive: ${intent.action}")

        if (intent.action == ACTION_UPDATE_WIDGET) {
            try {
                val data = WidgetData(
                    totalActivity = intent.getStringExtra(EXTRA_TOTAL_ACTIVITY) ?: "--:--:--",
                    duration = intent.getStringExtra(EXTRA_DURATION) ?: "--:--:--",
                    inactivity = intent.getStringExtra(EXTRA_INACTIVITY) ?: "--:--:--",
                    distance = intent.getDoubleExtra(EXTRA_DISTANCE, 0.0),
                    speed = intent.getFloatExtra(EXTRA_SPEED, 0.0f),
                    altitude = intent.getDoubleExtra(EXTRA_ALTITUDE, 0.0),
                    temperature = intent.getFloatExtra(EXTRA_TEMPERATURE, Float.MIN_VALUE),
                    barometer = intent.getFloatExtra(EXTRA_BAROMETER, 0.0f),
                    heartRate = intent.getIntExtra(EXTRA_HEARTRATE, 0),
                    isTracking = intent.getBooleanExtra(EXTRA_IS_TRACKING, false)
                )

                Log.d(TAG, "Updating widget: $data")

                val appWidgetManager = AppWidgetManager.getInstance(context)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(
                    ComponentName(context, GeoTrackerWidget::class.java)
                )

                Log.d(TAG, "Found ${appWidgetIds.size} widget instances")

                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId, data)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in onReceive", e)
            }
        }
    }

    override fun onEnabled(context: Context) {
        Log.d(TAG, "Widget enabled (first instance added)")
    }

    override fun onDisabled(context: Context) {
        Log.d(TAG, "Widget disabled (last instance removed)")
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
        val isTracking: Boolean = false
    )

    companion object {
        private const val TAG = "GeoTrackerWidget"
        const val ACTION_UPDATE_WIDGET = "at.co.netconsulting.geotracker.UPDATE_WIDGET"
        private const val EXTRA_TOTAL_ACTIVITY = "extra_total_activity"
        private const val EXTRA_DURATION = "extra_duration"
        private const val EXTRA_INACTIVITY = "extra_inactivity"
        private const val EXTRA_DISTANCE = "extra_distance"
        private const val EXTRA_SPEED = "extra_speed"
        private const val EXTRA_ALTITUDE = "extra_altitude"
        private const val EXTRA_TEMPERATURE = "extra_temperature"
        private const val EXTRA_BAROMETER = "extra_barometer"
        private const val EXTRA_HEARTRATE = "extra_heartrate"
        private const val EXTRA_IS_TRACKING = "extra_is_tracking"

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
                val intent = Intent(context, GeoTrackerWidget::class.java).apply {
                    action = ACTION_UPDATE_WIDGET
                    putExtra(EXTRA_TOTAL_ACTIVITY, totalActivity)
                    putExtra(EXTRA_DURATION, duration)
                    putExtra(EXTRA_INACTIVITY, inactivity)
                    putExtra(EXTRA_DISTANCE, distance)
                    putExtra(EXTRA_SPEED, speed)
                    putExtra(EXTRA_ALTITUDE, altitude)
                    putExtra(EXTRA_TEMPERATURE, temperature ?: Float.MIN_VALUE)
                    putExtra(EXTRA_BAROMETER, barometer)
                    putExtra(EXTRA_HEARTRATE, heartRate)
                    putExtra(EXTRA_IS_TRACKING, isTracking)
                }
                context.sendBroadcast(intent)
                Log.d(TAG, "Broadcast sent: totalActivity=$totalActivity, duration=$duration, inactivity=$inactivity, distance=$distance, speed=$speed, altitude=$altitude, temp=$temperature, baro=$barometer, hr=$heartRate, tracking=$isTracking")
            } catch (e: Exception) {
                Log.e(TAG, "Error sending widget update broadcast", e)
            }
        }

        fun updateWidgetNotTracking(context: Context) {
            updateWidget(context, "--:--:--", "--:--:--", "--:--:--", 0.0, 0.0f, 0.0, null, 0.0f, 0, false)
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            data: WidgetData
        ) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_geotracker)

                // Set click listener to open MainActivity
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                // Update status text
                if (data.isTracking) {
                    views.setTextViewText(R.id.widget_status, "Recording...")
                    views.setTextColor(R.id.widget_status, 0xFF4CAF50.toInt()) // Green
                } else {
                    views.setTextViewText(R.id.widget_status, "Not Recording")
                    views.setTextColor(R.id.widget_status, 0xFF808080.toInt()) // Gray
                }

                // Update values
                if (data.isTracking) {
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

                    // Speed in km/h (input is in m/s)
                    val speedKmh = data.speed * 3.6
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
