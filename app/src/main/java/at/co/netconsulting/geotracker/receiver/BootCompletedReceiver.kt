package at.co.netconsulting.geotracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking if backup needs to be rescheduled")

            // Check if auto backup was enabled
            val sharedPreferences = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
            val isBackupEnabled = sharedPreferences.getBoolean("autoBackupEnabled", false)

            if (isBackupEnabled) {
                val hour = sharedPreferences.getInt("backupHour", 2)
                val minute = sharedPreferences.getInt("backupMinute", 0)

                // Reschedule the backup
                Log.d(TAG, "Rescheduling auto backup after boot (time: $hour:$minute)")
                AutoBackupReceiver.scheduleBackup(context, hour, minute)
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}