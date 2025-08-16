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
import android.os.Environment
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import at.co.netconsulting.geotracker.MainActivity
import at.co.netconsulting.geotracker.R
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.gpx.saveGpxFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

/**
 * Service that performs automatic database backup and GPX export
 */
class AutoBackupService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private val NOTIFICATION_ID = 9876
    private val CHANNEL_ID = "auto_backup_channel"

    companion object {
        private const val TAG = "AutoBackupService"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("Auto backup service started")

        // Show notification immediately with proper foreground service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createProgressNotification(0),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, createProgressNotification(0))
        }

        // Acquire wake lock to ensure backup completes
        acquireWakeLock()

        // Start the backup in a coroutine
        serviceScope.launch {
            try {
                performAutoBackup()
            } catch (e: Exception) {
                Timber.e(e, "Error during auto backup")
            } finally {
                releaseWakeLock()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GeoTracker:AutoBackupWakeLock"
        ).apply {
            acquire(30 * 60 * 1000L) // 30 minutes max
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Auto Backup Channel"
            val descriptionText = "Channel for automatic backup notifications"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createProgressNotification(progress: Int): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Automatic Backup")
            .setContentText("Backing up database and runs: $progress%")
            .setSmallIcon(R.drawable.ic_start_marker)
            .setContentIntent(pendingIntent)
            .setProgress(100, progress, progress == 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotificationProgress(progress: Int) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createProgressNotification(progress))
    }

    private suspend fun performAutoBackup() {
        try {
            // Step 1: Backup the database (30% of progress)
            updateNotificationProgress(10)
            val backupSuccess = backupDatabase()
            updateNotificationProgress(30)

            if (!backupSuccess) {
                Timber.e("Database backup failed")
                showCompletionNotification(false, "Database backup failed")
                return
            }

            // Step 2: Clear existing GPX files before export (40% of progress)
            updateNotificationProgress(40)
            val clearSuccess = clearGpxFiles()

            if (!clearSuccess) {
                Timber.w("Warning: Could not clear some existing GPX files")
            }

            // Step 3: Export all runs to GPX files. We are using the same logic as we do for the manual export
            updateNotificationProgress(50)
            val gpxExportSuccess = exportAllEventsToGpx()
            updateNotificationProgress(100)

            // Final notification
            val allSuccess = backupSuccess && gpxExportSuccess
            val message = if (allSuccess) {
                "Database and GPX files backed up successfully"
            } else if (backupSuccess) {
                "Database backed up but GPX export had issues"
            } else {
                "Backup process encountered errors"
            }

            showCompletionNotification(allSuccess, message)

            // Record last successful backup time
            if (allSuccess || backupSuccess) {
                getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE).edit()
                    .putLong("lastBackupTime", System.currentTimeMillis())
                    .apply()
            }

        } catch (e: Exception) {
            Timber.e(e,"Error during automatic backup")
            showCompletionNotification(false, "Backup error: ${e.message}")
        }
    }

    /**
     * Clear existing GPX files from the GeoTracker folder to avoid duplicates
     * Preserves the DatabaseBackups subfolder
     */
    private suspend fun clearGpxFiles(): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                // Get the public Downloads directory where GPX files are actually saved
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val geoTrackerDir = File(downloadsDir, "GeoTracker")

                Timber.d("Attempting to clear files from: ${geoTrackerDir.absolutePath}")
                Timber.d("Downloads directory exists: ${downloadsDir.exists()}")
                Timber.d("GeoTracker directory exists: ${geoTrackerDir.exists()}")

                if (!downloadsDir.exists()) {
                    Timber.w("Downloads directory doesn't exist: ${downloadsDir.absolutePath}")
                    return@withContext false
                }

                if (!geoTrackerDir.exists()) {
                    Timber.d("GeoTracker directory doesn't exist at ${geoTrackerDir.absolutePath}, nothing to clear")
                    return@withContext true
                }

                if (!geoTrackerDir.canRead() || !geoTrackerDir.canWrite()) {
                    Timber.w("No read/write permission for GeoTracker directory: ${geoTrackerDir.absolutePath}")
                    return@withContext false
                }

                var clearedCount = 0
                var errorCount = 0

                // List all files for debugging
                val allFiles = geoTrackerDir.listFiles()
                Timber.d("Found ${allFiles?.size ?: 0} items in GeoTracker directory")
                allFiles?.forEach { file ->
                    Timber.d("Found item: ${file.name}, isFile: ${file.isFile}, isDirectory: ${file.isDirectory}")
                }

                // Iterate through all files in the GeoTracker directory
                allFiles?.forEach { file ->
                    try {
                        when {
                            // Skip the DatabaseBackups subdirectory
                            file.isDirectory && file.name == "DatabaseBackups" -> {
                                Timber.d("Skipping DatabaseBackups directory: ${file.name}")
                            }
                            // Delete GPX files
                            file.isFile && file.name.endsWith(".gpx", ignoreCase = true) -> {
                                if (file.delete()) {
                                    clearedCount++
                                    Timber.d("Deleted GPX file: ${file.name}")
                                } else {
                                    errorCount++
                                    Timber.w("Failed to delete GPX file: ${file.name}")
                                }
                            }
                            // Optionally delete other non-directory files (but preserve subdirectories)
                            file.isFile -> {
                                if (file.delete()) {
                                    clearedCount++
                                    Timber.d("Deleted file: ${file.name}")
                                } else {
                                    errorCount++
                                    Timber.w("Failed to delete file: ${file.name}")
                                }
                            }
                            // Log other directories that are being preserved
                            file.isDirectory -> {
                                Timber.d("Preserving directory: ${file.name}")
                            }
                        }
                    } catch (e: Exception) {
                        errorCount++
                        Timber.e(e, "Error processing file: ${file.name}")
                    }
                }

                Timber.i("File cleanup completed in ${geoTrackerDir.absolutePath}: $clearedCount files deleted, $errorCount errors")

                // Consider it successful if we had no errors, or if errors were minor compared to successes
                return@withContext errorCount == 0 || (clearedCount > 0 && errorCount < clearedCount)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during file cleanup")
            false
        }
    }

    /**
     * Export all events to GPX files using the SAME comprehensive logic as manual export
     * This includes ALL custom extensions: atemp, heart rate, speed, cadence, etc.
     */
    private suspend fun exportAllEventsToGpx(): Boolean {
        val database = FitnessTrackerDatabase.getInstance(applicationContext)
        return try {
            withContext(Dispatchers.IO) {
                // Get all events from the database - collect from Flow
                val allEvents = database.eventDao().getAllEvents().first()

                if (allEvents.isEmpty()) {
                    Timber.i("No events to export")
                    return@withContext true // Not an error, just no data
                }

                var successCount = 0
                var failureCount = 0

                // Export each event using the same logic as manual export
                for (event in allEvents) {
                    try {
                        val locations = database.locationDao().getLocationsByEventId(event.eventId)
                        val metrics = database.metricDao().getMetricsByEventId(event.eventId)
                        val weather = database.weatherDao().getWeatherForEvent(event.eventId)
                        val deviceStatus = database.deviceStatusDao().getLastDeviceStatusByEvent(event.eventId)

                        // Skip events with no location data
                        if (locations.isEmpty()) {
                            continue
                        }

                        // Map sport type to GPX activity type (same as manual export)
                        val activityType = when (event.artOfSport?.lowercase()) {
                            "running", "jogging", "marathon" -> "run"
                            "cycling", "bicycle", "bike", "biking" -> "bike"
                            "hiking", "walking", "trekking" -> "hike"
                            else -> event.artOfSport?.lowercase() ?: "unknown"
                        }

                        // Create GPX content with ALL the same extensions as manual export
                        val gpxBuilder = StringBuilder()
                        gpxBuilder.append("""<?xml version="1.0" encoding="UTF-8"?>
                                |<gpx version="1.1" 
                                |    creator="GeoTracker"
                                |    xmlns="http://www.topografix.com/GPX/1/1"
                                |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                |    xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1"
                                |    xmlns:custom="http://geotracker.netconsulting.at/xmlschemas/CustomExtension/v1"
                                |    xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd
                                |                        http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd">
                                |  <metadata>
                                |    <name>${event.eventName}</name>
                                |    <time>${event.eventDate}T00:00:00Z</time>
                                |  </metadata>
                                |  <trk>
                                |    <name>${event.eventName}</name>
                                |    <type>${activityType}</type>
                                |    <trkseg>
                            """.trimMargin())

                        locations.forEachIndexed { index, location ->
                            val metric = metrics.getOrNull(index)
                            val weatherInfo = weather.firstOrNull()
                            val deviceInfo = deviceStatus

                            // Only create a trackpoint if there's a valid metric with a valid timestamp
                            if (metric != null && metric.timeInMilliseconds > 0) {
                                val timestamp = Instant.ofEpochMilli(metric.timeInMilliseconds)
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                                gpxBuilder.append("""
        |      <trkpt lat="${location.latitude}" lon="${location.longitude}">
        |        <ele>${location.altitude}</ele>
        |        <time>${timestamp}</time>
        |        <extensions>
        |          <custom:distance>${metric.distance}</custom:distance>""")

                                // Add heart rate if available
                                if (metric.heartRate > 0) {
                                    gpxBuilder.append("""
        |          <gpxtpx:hr>${metric.heartRate}</gpxtpx:hr>""")
                                }

                                // Add air temperature if available (from weather data or metric)
                                val temperature = weatherInfo?.temperature ?: getMetricTemperature(metric)
                                temperature?.let { temp ->
                                    gpxBuilder.append("""
        |          <gpxtpx:atemp>${temp}</gpxtpx:atemp>""")
                                }

                                // Add lap number
                                gpxBuilder.append("""
        |          <custom:lap>${metric.lap}</custom:lap>""")

                                // Add activity type
                                gpxBuilder.append("""
        |          <custom:type>${activityType}</custom:type>""")

                                // Add satellite count if available
                                deviceInfo?.let { device ->
                                    if (device.numberOfSatellites.isNotEmpty()) {
                                        gpxBuilder.append("""
        |          <custom:sat>${device.numberOfSatellites}</custom:sat>""")
                                    }
                                }

                                // Add speed
                                if (metric.speed > 0) {
                                    gpxBuilder.append("""
        |          <custom:speed>${metric.speed}</custom:speed>""")
                                }

                                // Add cadence if available
                                metric.cadence?.let { cadence ->
                                    if (cadence > 0) {
                                        gpxBuilder.append("""
        |          <custom:cadence>${cadence}</custom:cadence>""")
                                    }
                                }

                                // Add GPS accuracy if available
                                getMetricAccuracy(metric)?.let { accuracy ->
                                    if (accuracy > 0) {
                                        gpxBuilder.append("""
        |          <custom:accuracy>${accuracy}</custom:accuracy>""")
                                    }
                                }

                                // Add steps if available
                                getMetricSteps(metric)?.let { steps ->
                                    if (steps > 0) {
                                        gpxBuilder.append("""
        |          <custom:steps>${steps}</custom:steps>""")
                                    }
                                }

                                // Add stride length if available
                                getMetricStrideLength(metric)?.let { strideLength ->
                                    if (strideLength > 0) {
                                        gpxBuilder.append("""
        |          <custom:stride_length>${strideLength}</custom:stride_length>""")
                                    }
                                }

                                gpxBuilder.append("""
        |        </extensions>
        |      </trkpt>
        """.trimMargin())
                            }
                        }

                        gpxBuilder.append("""
                            |    </trkseg>
                            |  </trk>
                            |</gpx>
                        """.trimMargin())

                        val filename = "${event.eventName}_${event.eventDate}.gpx"
                            .replace(" ", "_")
                            .replace(":", "-")
                            .replace("[^a-zA-Z0-9._-]".toRegex(), "_")

                        // Save file using the same storage approach as manual export
                        val success = saveGpxFile(applicationContext, filename, gpxBuilder.toString())

                        if (success) {
                            successCount++
                            Timber.d("Exported GPX for event: ${event.eventName}")
                        } else {
                            failureCount++
                            Timber.e("Failed to export GPX for event: ${event.eventName}")
                        }

                    } catch (e: Exception) {
                        Timber.e(e, "Error exporting event ${event.eventId}")
                        failureCount++
                    }
                }

                Timber.i("GPX Export completed: $successCount succeeded, $failureCount failed")
                return@withContext failureCount == 0
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during automatic GPX export")
            false
        }
    }

    // Helper functions to safely access new fields using reflection (same as manual export)
    private fun getMetricTemperature(metric: at.co.netconsulting.geotracker.domain.Metric): Float? {
        return try {
            val field = metric::class.java.getDeclaredField("temperature")
            field.isAccessible = true
            field.get(metric) as? Float
        } catch (e: Exception) {
            null
        }
    }

    private fun getMetricAccuracy(metric: at.co.netconsulting.geotracker.domain.Metric): Float? {
        return try {
            val field = metric::class.java.getDeclaredField("accuracy")
            field.isAccessible = true
            field.get(metric) as? Float
        } catch (e: Exception) {
            null
        }
    }

    private fun getMetricSteps(metric: at.co.netconsulting.geotracker.domain.Metric): Int? {
        return try {
            val field = metric::class.java.getDeclaredField("steps")
            field.isAccessible = true
            field.get(metric) as? Int
        } catch (e: Exception) {
            null
        }
    }

    private fun getMetricStrideLength(metric: at.co.netconsulting.geotracker.domain.Metric): Float? {
        return try {
            val field = metric::class.java.getDeclaredField("strideLength")
            field.isAccessible = true
            field.get(metric) as? Float
        } catch (e: Exception) {
            null
        }
    }

    private fun showCompletionNotification(success: Boolean, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title = if (success) "Backup Completed Successfully" else "Backup Completed with Issues"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_start_marker)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    private fun backupDatabase(): Boolean {
        return try {
            // Create the backup directory in the same location as the GPX files
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupDir = File(downloadsDir, "GeoTracker/DatabaseBackups")

            Timber.d("Database backup directory: ${backupDir.absolutePath}")

            if (!backupDir.exists()) {
                val created = backupDir.mkdirs()
                Timber.d("Created backup directory: $created")
                if (!created) {
                    Timber.e("Failed to create backup directory: ${backupDir.absolutePath}")
                    return false
                }
            }

            // Get current date/time for the filename
            val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val currentDateTime = dateFormat.format(Date())

            // Create the backup file
            val backupFile = File(backupDir, "geotracker_backup_$currentDateTime.db")

            // Get the database file
            val dbFile = getDatabasePath("fitness_tracker.db")

            // Copy the database file to the backup location
            dbFile.inputStream().use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output)
                }
            }

            Timber.d("Database backup successful: ${backupFile.absolutePath}")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error backing up database")
            false
        }
    }
}