package at.co.netconsulting.geotracker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import at.co.netconsulting.geotracker.MainActivity
import at.co.netconsulting.geotracker.R
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DatabaseBackupService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val NOTIFICATION_ID = 3000
    private val CHANNEL_ID = "DatabaseBackupChannel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Starting database backup...")
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            try {
                performBackup()
            } catch (e: Exception) {
                Log.e("DatabaseBackupService", "Backup failed", e)
                updateNotification("Backup failed: ${e.message}")
            }
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Database Backup Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Used for database backup operations"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(message: String): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Database Backup")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        val notification = createNotification(message)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun performBackup() {
        updateNotification("Preparing backup...")

        // Get database instance
        val database = FitnessTrackerDatabase.getInstance(applicationContext)

        // Create backup directory if it doesn't exist
        val backupDir = File(applicationContext.getExternalFilesDir(null), "backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        // Generate backup filename with timestamp
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(backupDir, "fitness_tracker_backup_$timestamp.zip")

        try {
            updateNotification("Backing up database...")

            // Create ZIP file containing all database files
            ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
                // Get database file - using correct database name with .db extension
                val dbFile = applicationContext.getDatabasePath("fitness_tracker.db")

                // Also backup the -shm and -wal files if they exist
                val dbFiles = listOf(
                    dbFile,
                    File(dbFile.path + "-shm"),
                    File(dbFile.path + "-wal")
                )

                // Add each database file to ZIP if it exists
                dbFiles.forEach { file ->
                    if (file.exists()) {
                        zipOut.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }

                // Backup shared preferences
                val sharedPrefsDir = File(applicationContext.applicationInfo.dataDir, "shared_prefs")
                if (sharedPrefsDir.exists()) {
                    sharedPrefsDir.listFiles()?.forEach { prefsFile ->
                        zipOut.putNextEntry(ZipEntry("shared_prefs/${prefsFile.name}"))
                        prefsFile.inputStream().use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }
            }

            updateNotification("Backup completed successfully: ${backupFile.name}")

            // Stop the service after a short delay to ensure the notification is seen
            Thread.sleep(3000)
            stopSelf()

        } catch (e: Exception) {
            Log.e("DatabaseBackupService", "Error during backup", e)
            updateNotification("Backup failed: ${e.message}")
            throw e
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
        }
    }
}