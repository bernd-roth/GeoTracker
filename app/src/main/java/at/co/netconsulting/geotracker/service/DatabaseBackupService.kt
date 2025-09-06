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
import at.co.netconsulting.geotracker.data.BackupProgress
import at.co.netconsulting.geotracker.data.BackupPhase
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
        val initialProgress = BackupProgress(isBackingUp = true, currentPhase = BackupPhase.INITIALIZING)
        val notification = createNotification(initialProgress)
        startForeground(NOTIFICATION_ID, notification)

        serviceScope.launch {
            try {
                performBackup()
            } catch (e: Exception) {
                Log.e("DatabaseBackupService", "Backup failed", e)
                val failedProgress = BackupProgress(currentPhase = BackupPhase.FAILED)
                updateNotification(failedProgress)
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

    private fun createNotification(backupProgress: BackupProgress): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = when (backupProgress.currentPhase) {
            BackupPhase.BACKING_UP_DATABASE -> "Backing up database: ${backupProgress.getProgressPercentage()}%"
            BackupPhase.COMPLETED -> "Database backup completed successfully"
            BackupPhase.FAILED -> "Database backup failed"
            else -> "Starting database backup..."
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Database Backup")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        // Show current file being processed if available
        if (backupProgress.currentFileName.isNotEmpty()) {
            builder.setSubText("Processing: ${backupProgress.currentFileName}")
        }

        // Set progress bar
        when (backupProgress.currentPhase) {
            BackupPhase.COMPLETED, BackupPhase.FAILED -> {
                // No progress bar for final states
            }
            else -> {
                builder.setProgress(100, backupProgress.getProgressPercentage(), false)
            }
        }

        return builder.build()
    }

    private fun updateNotification(backupProgress: BackupProgress) {
        val notification = createNotification(backupProgress)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private suspend fun performBackup() {
        var currentProgress = BackupProgress(
            isBackingUp = true,
            overallProgress = 0.1f,
            currentPhase = BackupPhase.BACKING_UP_DATABASE,
            status = "Preparing backup..."
        )
        updateNotification(currentProgress)

        // Get database instance
        val database = FitnessTrackerDatabase.getInstance(applicationContext)

        // Create backup directory if it doesn't exist
        val backupDir = File(applicationContext.getExternalFilesDir(null), "backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        currentProgress = currentProgress.copy(overallProgress = 0.2f, status = "Creating backup directory...")
        updateNotification(currentProgress)

        // Generate backup filename with timestamp
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(backupDir, "fitness_tracker_backup_$timestamp.zip")

        try {
            currentProgress = currentProgress.copy(overallProgress = 0.3f, status = "Starting database backup...")
            updateNotification(currentProgress)

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

                val existingFiles = dbFiles.filter { it.exists() }
                val totalFiles = existingFiles.size

                // Get shared preferences files
                val sharedPrefsDir = File(applicationContext.applicationInfo.dataDir, "shared_prefs")
                val prefsFiles = if (sharedPrefsDir.exists()) {
                    sharedPrefsDir.listFiles()?.toList() ?: emptyList()
                } else {
                    emptyList()
                }

                val allFiles = existingFiles + prefsFiles
                val totalFileCount = allFiles.size

                currentProgress = currentProgress.copy(
                    overallProgress = 0.4f,
                    totalFiles = totalFileCount,
                    filesProcessed = 0,
                    status = "Backing up database files..."
                )
                updateNotification(currentProgress)

                // Add each database file to ZIP if it exists
                existingFiles.forEachIndexed { index, file ->
                    currentProgress = currentProgress.copy(
                        filesProcessed = index + 1,
                        currentFileName = file.name,
                        overallProgress = 0.4f + (index.toFloat() / totalFileCount * 0.4f) // 40% to 80%
                    )
                    updateNotification(currentProgress)

                    zipOut.putNextEntry(ZipEntry(file.name))
                    file.inputStream().use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }

                // Backup shared preferences
                prefsFiles.forEachIndexed { index, prefsFile ->
                    val totalProcessed = existingFiles.size + index + 1
                    currentProgress = currentProgress.copy(
                        filesProcessed = totalProcessed,
                        currentFileName = "shared_prefs/${prefsFile.name}",
                        overallProgress = 0.4f + (totalProcessed.toFloat() / totalFileCount * 0.4f) // Continue from database files
                    )
                    updateNotification(currentProgress)

                    zipOut.putNextEntry(ZipEntry("shared_prefs/${prefsFile.name}"))
                    prefsFile.inputStream().use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }

            val completedProgress = BackupProgress(
                isBackingUp = false,
                overallProgress = 1.0f,
                currentPhase = BackupPhase.COMPLETED,
                filesProcessed = currentProgress.totalFiles,
                totalFiles = currentProgress.totalFiles,
                status = "Backup completed successfully"
            )
            updateNotification(completedProgress)

            // Stop the service after a short delay to ensure the notification is seen
            Thread.sleep(3000)
            stopSelf()

        } catch (e: Exception) {
            Log.e("DatabaseBackupService", "Error during backup", e)
            val failedProgress = BackupProgress(currentPhase = BackupPhase.FAILED)
            updateNotification(failedProgress)
            throw e
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.launch {
        }
    }
}