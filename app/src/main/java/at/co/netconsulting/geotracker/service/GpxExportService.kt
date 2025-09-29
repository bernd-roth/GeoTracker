package at.co.netconsulting.geotracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class GpxExportService : Service() {
    private val NOTIFICATION_ID = 2002
    private val CHANNEL_ID = "GPXExportService"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification("Starting GPX export..."))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        CoroutineScope(Dispatchers.IO).launch {
            exportAllEvents()
        }
        return START_NOT_STICKY
    }

    private suspend fun exportAllEvents() {
        try {
            val database = FitnessTrackerDatabase.getInstance(applicationContext)

            // Get events from the Flow by collecting the first emission
            val events = database.eventDao().getAllEvents().first()

            updateNotification("Found ${events.size} events to export")

            events.forEachIndexed { index, event ->
                try {
                    val eventId = event.eventId.toInt()
                    val locations = database.locationDao().getLocationsByEventId(eventId)
                    val metrics = database.metricDao().getMetricsByEventId(eventId)

                    if (locations.isNotEmpty() && locations.any { it.latitude != 0.0 && it.longitude != 0.0 }) {
                        val gpxContent = createGPXContent(event, locations, metrics)
                        saveGPXFile(event, gpxContent)
                        updateNotification("Exported ${index + 1}/${events.size} events")
                    }
                } catch (e: Exception) {
                    Log.e("GPXExport", "Error exporting event: ${e.message}")
                    e.printStackTrace()
                }
            }

            updateNotification("Export completed! ${events.size} files exported")
            stopSelf()
        } catch (e: Exception) {
            Log.e("GPXExport", "Error during export: ${e.message}")
            e.printStackTrace()
            updateNotification("Export failed: ${e.message}")
            stopSelf()
        }
    }

    private fun createGPXContent(event: Event, locations: List<Location>, metrics: List<Metric>): String {
        val xmlBuilder = StringBuilder()
        xmlBuilder.append("""<?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" 
                creator="GeoTracker"
                xmlns="http://www.topografix.com/GPX/1/1"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
            """.trimIndent())

        // Add metadata
        xmlBuilder.append("""
            <metadata>
                <name>${event.eventName}</name>
                <time>${event.eventDate}T00:00:00Z</time>
            </metadata>
        """.trimIndent())

        // Start track
        xmlBuilder.append("""
            <trk>
                <name>${event.eventName}</name>
                <desc>${event.comment ?: ""}</desc>
                <type>${event.artOfSport}</type>
                <trkseg>
        """.trimIndent())

        // Add trackpoints
        locations.forEachIndexed { index, location ->
            val metric = metrics.getOrNull(index)
            val time = if (metric != null) {
                Instant.ofEpochMilli(metric.timeInMilliseconds)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_INSTANT)
            } else {
                Instant.now().toString()
            }

            xmlBuilder.append("""
                <trkpt lat="${location.latitude}" lon="${location.longitude}">
                    <ele>${location.altitude}</ele>
                    <time>${time}</time>
                    ${metric?.speed?.let { "<speed>$it</speed>" } ?: ""}
                    ${metric?.heartRate?.takeIf { it > 0 }?.let { "<hr>$it</hr>" } ?: ""}
                </trkpt>
            """.trimIndent())
        }

        // Close track
        xmlBuilder.append("""
                </trkseg>
            </trk>
            </gpx>
        """.trimIndent())

        return xmlBuilder.toString()
    }

    private fun saveGPXFile(event: Event, content: String) {
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "GeoTracker"
        )
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val fileName = "${event.eventName}_${event.eventDate}.gpx"
        val file = File(directory, fileName)

        FileWriter(file).use { writer ->
            writer.write(content)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPX Export Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPX Export")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}