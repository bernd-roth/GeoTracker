package at.co.netconsulting.geotracker.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import at.co.netconsulting.geotracker.MainActivity
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

/**
 * Result of database import operation
 */
data class ImportResult(
    val isSuccess: Boolean,
    val error: String? = null
)

/**
 * Utility class for importing database backups
 */
object DatabaseImporter {
    private const val TAG = "DatabaseImporter"
    private const val SQLITE_HEADER = "SQLite format 3\u0000"
    private const val ZIP_HEADER_1 = 0x50 // 'P'
    private const val ZIP_HEADER_2 = 0x4B // 'K'
    private const val MIN_DB_SIZE = 4096L // Minimum valid SQLite database size

    /**
     * Import a database backup file (supports .db and .zip formats) and restart the app
     *
     * @param context Application context
     * @param sourceUri URI of the backup database file to import
     * @return ImportResult indicating success or failure with error message
     */
    suspend fun importDatabase(context: Context, sourceUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        var safetyBackupDir: File? = null

        try {
            Log.d(TAG, "Starting database import from URI: $sourceUri")

            // Step 1: Determine file type and validate
            val isZipFile = isZipFile(context, sourceUri)
            Log.d(TAG, "File type: ${if (isZipFile) "ZIP" else "DB"}")

            if (!isZipFile && !validateDatabaseFile(context, sourceUri)) {
                Log.e(TAG, "Database validation failed")
                return@withContext ImportResult(false, "Invalid database file")
            }
            Log.d(TAG, "File validation successful")

            // Step 2: Create safety backup of current database and SharedPreferences
            safetyBackupDir = createSafetyBackup(context)
            if (safetyBackupDir == null) {
                Log.e(TAG, "Failed to create safety backup")
                return@withContext ImportResult(false, "Failed to create safety backup")
            }
            Log.d(TAG, "Safety backup created at: ${safetyBackupDir.absolutePath}")

            // Step 3: Close database connection
            try {
                val db = FitnessTrackerDatabase.getInstance(context)
                db.close()
                Log.d(TAG, "Database closed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing database", e)
                restoreFromSafetyBackup(context, safetyBackupDir)
                return@withContext ImportResult(false, "Failed to close database: ${e.message}")
            }

            // Step 4: Replace database files and SharedPreferences
            try {
                if (isZipFile) {
                    restoreFromZipFile(context, sourceUri)
                } else {
                    replaceDatabaseFiles(context, sourceUri)
                }
                Log.d(TAG, "Database and settings restored successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error replacing database files", e)
                restoreFromSafetyBackup(context, safetyBackupDir)
                return@withContext ImportResult(false, "Failed to restore backup: ${e.message}")
            }

            Log.d(TAG, "Database import completed successfully")
            return@withContext ImportResult(true)

        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during import", e)
            // Attempt to restore from safety backup if it exists
            safetyBackupDir?.let { restoreFromSafetyBackup(context, it) }
            return@withContext ImportResult(false, "Import failed: ${e.message}")
        }
    }

    /**
     * Check if the file is a ZIP archive
     */
    private fun isZipFile(context: Context, uri: Uri): Boolean {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val header = ByteArray(2)
                val bytesRead = inputStream.read(header)
                if (bytesRead >= 2) {
                    return header[0].toInt() == ZIP_HEADER_1 && header[1].toInt() == ZIP_HEADER_2
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking file type", e)
        }
        return false
    }

    /**
     * Validate that the file is a valid SQLite database
     */
    private fun validateDatabaseFile(context: Context, uri: Uri): Boolean {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Check file size
                val fileSize = inputStream.available().toLong()
                if (fileSize < MIN_DB_SIZE) {
                    Log.e(TAG, "File too small: $fileSize bytes")
                    return false
                }

                // Check SQLite header (first 16 bytes)
                val header = ByteArray(16)
                val bytesRead = inputStream.read(header)
                if (bytesRead < 16) {
                    Log.e(TAG, "Could not read file header")
                    return false
                }

                val headerString = String(header, Charsets.UTF_8)
                if (!headerString.startsWith(SQLITE_HEADER)) {
                    Log.e(TAG, "Invalid SQLite header")
                    return false
                }

                return true
            } ?: run {
                Log.e(TAG, "Could not open input stream")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error validating database file", e)
            return false
        }
    }

    /**
     * Create a safety backup of the current database and SharedPreferences before importing
     *
     * @return Directory containing the safety backup, or null if failed
     */
    private fun createSafetyBackup(context: Context): File? {
        try {
            // Create timestamped backup directory
            val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
            val backupDir = File(
                context.getExternalFilesDir(null),
                "DatabaseBackups/import_safety_backup_$timestamp"
            )

            if (!backupDir.exists() && !backupDir.mkdirs()) {
                Log.e(TAG, "Failed to create backup directory")
                return null
            }

            // Get database files
            val dbFile = context.getDatabasePath("fitness_tracker.db")
            val dbFiles = listOf(
                dbFile,
                File(dbFile.path + "-shm"),
                File(dbFile.path + "-wal")
            )

            // Copy each existing database file to backup directory
            dbFiles.filter { it.exists() }.forEach { file ->
                val backupFile = File(backupDir, file.name)
                file.copyTo(backupFile, overwrite = true)
                Log.d(TAG, "Backed up database file: ${file.name}")
            }

            // Backup SharedPreferences files
            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            if (sharedPrefsDir.exists()) {
                val prefsBackupDir = File(backupDir, "shared_prefs")
                if (!prefsBackupDir.exists() && !prefsBackupDir.mkdirs()) {
                    Log.w(TAG, "Failed to create shared_prefs backup directory")
                } else {
                    sharedPrefsDir.listFiles()?.forEach { prefsFile ->
                        val backupFile = File(prefsBackupDir, prefsFile.name)
                        prefsFile.copyTo(backupFile, overwrite = true)
                        Log.d(TAG, "Backed up SharedPreferences: ${prefsFile.name}")
                    }
                }
            }

            // Clean up old safety backups (keep last 3)
            cleanupOldSafetyBackups(context)

            return backupDir
        } catch (e: Exception) {
            Log.e(TAG, "Error creating safety backup", e)
            return null
        }
    }

    /**
     * Remove old safety backups, keeping only the 3 most recent
     */
    private fun cleanupOldSafetyBackups(context: Context) {
        try {
            val backupsDir = File(context.getExternalFilesDir(null), "DatabaseBackups")
            if (!backupsDir.exists()) return

            val safetyBackups = backupsDir.listFiles { file ->
                file.isDirectory && file.name.startsWith("import_safety_backup_")
            }?.sortedByDescending { it.lastModified() } ?: return

            // Delete all but the 3 most recent
            safetyBackups.drop(3).forEach { dir ->
                dir.deleteRecursively()
                Log.d(TAG, "Deleted old safety backup: ${dir.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old safety backups", e)
        }
    }

    /**
     * Replace the current database files with the imported file
     */
    private fun replaceDatabaseFiles(context: Context, sourceUri: Uri) {
        // Get target database file path
        val dbFile = context.getDatabasePath("fitness_tracker.db")

        // Copy the imported file to the database location
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            FileOutputStream(dbFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: throw Exception("Cannot open source file")

        // Delete WAL and SHM files as they're no longer valid for the new database
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-wal").delete()

        Log.d(TAG, "Database files replaced successfully")
    }

    /**
     * Restore database and SharedPreferences from a ZIP backup file
     */
    private fun restoreFromZipFile(context: Context, sourceUri: Uri) {
        val tempDir = File(context.cacheDir, "import_temp_${System.currentTimeMillis()}")

        try {
            // Create temp directory for extraction
            if (!tempDir.exists() && !tempDir.mkdirs()) {
                throw Exception("Failed to create temp directory")
            }

            // Extract ZIP file to temp directory
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var entry: ZipEntry? = zipInputStream.nextEntry
                    while (entry != null) {
                        val entryFile = File(tempDir, entry.name)

                        if (entry.isDirectory) {
                            entryFile.mkdirs()
                        } else {
                            // Ensure parent directory exists
                            entryFile.parentFile?.mkdirs()

                            // Extract file
                            FileOutputStream(entryFile).use { outputStream ->
                                zipInputStream.copyTo(outputStream)
                            }
                            Log.d(TAG, "Extracted: ${entry.name}")
                        }

                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                }
            } ?: throw Exception("Cannot open ZIP file")

            // Restore database files
            val dbFile = context.getDatabasePath("fitness_tracker.db")
            val extractedDbFile = File(tempDir, "fitness_tracker.db")

            if (extractedDbFile.exists()) {
                extractedDbFile.copyTo(dbFile, overwrite = true)
                Log.d(TAG, "Restored database from ZIP")

                // Delete WAL and SHM files as they're no longer valid
                File(dbFile.path + "-shm").delete()
                File(dbFile.path + "-wal").delete()
            } else {
                throw Exception("Database file not found in ZIP archive")
            }

            // Restore SharedPreferences if they exist in the ZIP
            val extractedPrefsDir = File(tempDir, "shared_prefs")
            if (extractedPrefsDir.exists() && extractedPrefsDir.isDirectory) {
                val targetPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")

                extractedPrefsDir.listFiles()?.forEach { prefsFile ->
                    val targetFile = File(targetPrefsDir, prefsFile.name)
                    prefsFile.copyTo(targetFile, overwrite = true)
                    Log.d(TAG, "Restored SharedPreferences: ${prefsFile.name}")
                }
            } else {
                Log.d(TAG, "No SharedPreferences found in ZIP, skipping")
            }

        } finally {
            // Clean up temp directory
            tempDir.deleteRecursively()
        }
    }

    /**
     * Restore the database and SharedPreferences from safety backup in case of import failure
     */
    private fun restoreFromSafetyBackup(context: Context, backupDir: File) {
        try {
            Log.d(TAG, "Restoring from safety backup: ${backupDir.absolutePath}")

            // Restore database files
            val dbFile = context.getDatabasePath("fitness_tracker.db")
            val backupFiles = backupDir.listFiles()?.filter { !it.isDirectory } ?: emptyList()

            backupFiles.forEach { backupFile ->
                val targetFile = if (backupFile.name == "fitness_tracker.db") {
                    dbFile
                } else {
                    File(dbFile.parent, backupFile.name)
                }

                backupFile.copyTo(targetFile, overwrite = true)
                Log.d(TAG, "Restored database file: ${backupFile.name}")
            }

            // Restore SharedPreferences if they exist in backup
            val prefsBackupDir = File(backupDir, "shared_prefs")
            if (prefsBackupDir.exists() && prefsBackupDir.isDirectory) {
                val targetPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")

                prefsBackupDir.listFiles()?.forEach { prefsFile ->
                    val targetFile = File(targetPrefsDir, prefsFile.name)
                    prefsFile.copyTo(targetFile, overwrite = true)
                    Log.d(TAG, "Restored SharedPreferences: ${prefsFile.name}")
                }
            }

            Log.d(TAG, "Database and SharedPreferences restored from safety backup")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring from safety backup", e)
        }
    }

    /**
     * Restart the application
     * Uses AlarmManager with delayed PendingIntent to ensure clean shutdown and restart
     */
    fun restartApp(context: Context) {
        try {
            Log.d(TAG, "Restarting application")

            // Create intent to restart MainActivity
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Schedule restart with 500ms delay to allow cleanup
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.setExact(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 500,
                pendingIntent
            )

            // Graceful exit
            exitProcess(0)
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting app", e)
        }
    }
}
