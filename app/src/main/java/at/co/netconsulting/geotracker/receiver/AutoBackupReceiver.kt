package at.co.netconsulting.geotracker.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import at.co.netconsulting.geotracker.service.AutoBackupService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * BroadcastReceiver that gets triggered by the AlarmManager to start the backup process
 */
class AutoBackupReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Auto backup alarm received")

        // Check if auto backup is still enabled (user might have disabled it)
        val sharedPreferences = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        val isBackupEnabled = sharedPreferences.getBoolean("autoBackupEnabled", false)

        if (!isBackupEnabled) {
            Log.d(TAG, "Auto backup is disabled, ignoring scheduled alarm")
            return
        }

        // Start the backup service
        try {
            val serviceIntent = Intent(context, AutoBackupService::class.java)

            // For Android 14+ (API 34+), we must specify foreground service type when starting a foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Starting with Android 14, we need both FLAG_FOREGROUND_SERVICE and specify the type
                serviceIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                ContextCompat.startForegroundService(context, serviceIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // For Android 8.0+ but lower than 14
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Log.d(TAG, "Auto backup service started")

            // Schedule the next backup for tomorrow
            rescheduleNextBackup(context)

            // Update last triggered time
            context.getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE).edit()
                .putLong("lastBackupTriggerTime", System.currentTimeMillis())
                .apply()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting backup service", e)
        }
    }

    /**
     * Reschedules the next backup for tomorrow at the same time
     */
    private fun rescheduleNextBackup(context: Context) {
        try {
            val sharedPreferences = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
            val hour = sharedPreferences.getInt("backupHour", 2)
            val minute = sharedPreferences.getInt("backupMinute", 0)

            // Use our scheduling function
            scheduleBackup(context, hour, minute)
        } catch (e: Exception) {
            Log.e(TAG, "Error rescheduling backup", e)
        }
    }

    companion object {
        private const val TAG = "AutoBackupReceiver"
        private const val AUTO_BACKUP_REQUEST_CODE = 1234

        /**
         * Schedule backup at the specified hour and minute, for tomorrow
         */
        fun scheduleBackup(context: Context, hour: Int, minute: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Create intent for the backup receiver
            val intent = Intent(context, AutoBackupReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                AUTO_BACKUP_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Calculate time for the alarm
            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)

                // If the time is already passed today, add one day
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            // Set the exact alarm if possible
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } catch (e: Exception) {
                    // Fallback if exact alarms are not allowed
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            // Store next backup time for user information
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val nextBackupTimeStr = dateFormat.format(calendar.time)

            context.getSharedPreferences("BackupPrefs", Context.MODE_PRIVATE).edit()
                .putString("nextBackupTime", nextBackupTimeStr)
                .apply()

            Log.d(TAG, "Next backup scheduled for $nextBackupTimeStr")
        }
    }
}