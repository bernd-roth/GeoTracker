package at.co.netconsulting.geotracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import at.co.netconsulting.geotracker.MainActivity
import at.co.netconsulting.geotracker.R
import at.co.netconsulting.geotracker.widget.GeoTrackerWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class WidgetFollowingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var collectionJob: Job? = null
    private var activeUsersMonitorJob: Job? = null

    // Per-session tracking data
    private data class SessionTracker(
        val sessionId: String,
        val person: String,
        var startTimeMs: Long = 0L,
        var lastKnownTemperature: Float? = null,
        var lastKnownBarometer: Float? = null
    )

    private val trackedSessions = mutableMapOf<String, SessionTracker>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WidgetFollowingService started")

        loadConfig()

        if (trackedSessions.isEmpty()) {
            Log.d(TAG, "No configured sessions, stopping service")
            stopSelf()
            return START_NOT_STICKY
        }

        val personNames = trackedSessions.values.joinToString(", ") { it.person }
        val notification = createNotification(personNames)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startFollowing()

        return START_STICKY
    }

    private fun loadConfig() {
        trackedSessions.clear()
        val prefs = getSharedPreferences(GeoTrackerWidget.WIDGET_CONFIG_PREFS, Context.MODE_PRIVATE)
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(this, GeoTrackerWidget::class.java)
        )

        for (id in widgetIds) {
            val sessionId = prefs.getString("widget_${id}_session_id", null) ?: continue
            val person = prefs.getString("widget_${id}_person", null) ?: continue
            if (sessionId !in trackedSessions) {
                trackedSessions[sessionId] = SessionTracker(sessionId, person)
                Log.d(TAG, "Loaded config: sessionId=$sessionId, person=$person (widget $id)")
            }
        }
        Log.d(TAG, "Total tracked sessions: ${trackedSessions.size}")
    }

    private fun formatElapsedTime(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun startFollowing() {
        collectionJob?.cancel()
        activeUsersMonitorJob?.cancel()

        val sessionIds = trackedSessions.keys.toList()
        if (sessionIds.isEmpty()) return

        val followingService = FollowingService.getInstance(this)
        followingService.connect()

        collectionJob = serviceScope.launch {
            // Wait for connection
            var attempts = 0
            while (!followingService.connectionState.value && attempts < 30) {
                delay(1000)
                attempts++
            }

            if (!followingService.connectionState.value) {
                Log.e(TAG, "Could not connect to WebSocket after $attempts attempts")
                stopSelf()
                return@launch
            }

            // Start following all configured users
            followingService.followUsers(sessionIds)

            // Collect state updates
            followingService.followingState.collect { state ->
                val isRecording = getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                    .getBoolean("is_recording", false)

                if (isRecording) {
                    Log.d(TAG, "Recording active, skipping widget following updates")
                    return@collect
                }

                // Update each tracked session's widget
                for ((sessionId, tracker) in trackedSessions) {
                    val latestPoint = state.getCurrentPosition(sessionId) ?: continue

                    // Track start time from first data point
                    if (tracker.startTimeMs == 0L) {
                        tracker.startTimeMs = System.currentTimeMillis()
                    }

                    // Compute elapsed duration
                    val elapsed = System.currentTimeMillis() - tracker.startTimeMs
                    val duration = formatElapsedTime(elapsed)

                    // Cache weather values - only update when new data arrives
                    latestPoint.temperature?.toFloat()?.let { tracker.lastKnownTemperature = it }
                    latestPoint.pressure?.toFloat()?.let { tracker.lastKnownBarometer = it }

                    GeoTrackerWidget.updateWidgetFollowing(
                        this@WidgetFollowingService,
                        latestPoint,
                        duration,
                        tracker.lastKnownTemperature,
                        tracker.lastKnownBarometer
                    )
                }
            }
        }

        // Monitor active users - reset widgets when followed runners go offline
        activeUsersMonitorJob = serviceScope.launch {
            val wasActive = mutableSetOf<String>()
            followingService.activeUsers.collect { users ->
                val activeSessionIds = users.map { it.sessionId }.toSet()

                for ((sessionId, tracker) in trackedSessions) {
                    val isStillActive = sessionId in activeSessionIds
                    if (sessionId in wasActive && !isStillActive) {
                        Log.d(TAG, "Followed user ${tracker.person} went offline, resetting their widgets")
                        GeoTrackerWidget.resetFollowingWidgets(this@WidgetFollowingService, sessionId)
                    }
                    if (isStillActive) {
                        wasActive.add(sessionId)
                    }
                }

                // If all tracked sessions are offline, stop service
                val anyStillActive = trackedSessions.keys.any { it in activeSessionIds }
                if (wasActive.isNotEmpty() && !anyStillActive) {
                    Log.d(TAG, "All followed users went offline, stopping service")
                    stopSelf()
                    return@collect
                }
            }
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "WidgetFollowingService destroyed")
        collectionJob?.cancel()
        activeUsersMonitorJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Widget Following",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when widget is following a runner"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(personNames: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Following $personNames")
            .setContentText("Widget showing live stats")
            .setSmallIcon(R.drawable.ic_start_marker)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "WidgetFollowingSvc"
        private const val CHANNEL_ID = "WidgetFollowingChannel"
        private const val NOTIFICATION_ID = 9877
    }
}
