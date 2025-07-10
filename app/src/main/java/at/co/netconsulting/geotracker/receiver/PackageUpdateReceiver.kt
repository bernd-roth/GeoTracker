package at.co.netconsulting.geotracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.reminder.ReminderManager
import at.co.netconsulting.geotracker.tools.AlarmPermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PackageUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Log.d("PackageUpdateReceiver", "App was updated/replaced, rescheduling alarms")
                rescheduleAllAlarms(context, "APP_UPDATE")
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                val packageName = intent.data?.schemeSpecificPart
                if (packageName == context.packageName) {
                    Log.d("PackageUpdateReceiver", "Our package was replaced, rescheduling alarms")
                    rescheduleAllAlarms(context, "PACKAGE_REPLACE")
                }
            }
        }
    }

    private fun rescheduleAllAlarms(context: Context, reason: String) {
        // Use background scope to handle database operations
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check if we have exact alarm permission first
                if (!AlarmPermissionHelper.checkExactAlarmPermission(context)) {
                    Log.w("PackageUpdateReceiver", "No exact alarm permission, cannot reschedule alarms")
                    // Store flag to reschedule when permission is granted
                    context.getSharedPreferences("AlarmState", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("needs_rescheduling", true)
                        .putString("reschedule_reason", reason)
                        .apply()
                    return@launch
                }

                val database = FitnessTrackerDatabase.getInstance(context)
                val reminderManager = ReminderManager(context)

                // Get current user ID
                val currentUserId = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                    .getInt("current_user_id", 1)

                val plannedEvents = database.plannedEventDao().getPlannedEventsForUser(currentUserId)

                var rescheduledCount = 0
                var recurringCount = 0
                var singleCount = 0

                plannedEvents.forEach { event ->
                    if (event.isReminderActive && event.reminderDateTime.isNotEmpty()) {
                        try {
                            reminderManager.scheduleReminder(event)
                            rescheduledCount++

                            if (event.isRecurring) {
                                recurringCount++
                            } else {
                                singleCount++
                            }

                            val reminderType = if (event.isRecurring) "recurring" else "single"
                            Log.d("PackageUpdateReceiver", "Rescheduled $reminderType reminder for: ${event.plannedEventName}")
                        } catch (e: Exception) {
                            Log.e("PackageUpdateReceiver", "Failed to reschedule reminder for ${event.plannedEventName}", e)
                        }
                    }
                }

                // Also reschedule auto backup if enabled
                rescheduleAutoBackup(context)

                // Store success state
                context.getSharedPreferences("AlarmState", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("needs_rescheduling", false)
                    .putLong("last_reschedule_time", System.currentTimeMillis())
                    .putString("last_reschedule_reason", reason)
                    .apply()

                Log.d("PackageUpdateReceiver",
                    "Successfully rescheduled $rescheduledCount alarms ($recurringCount recurring, $singleCount single) due to $reason")

            } catch (e: Exception) {
                Log.e("PackageUpdateReceiver", "Failed to reschedule alarms after $reason", e)

                // Store error state
                context.getSharedPreferences("AlarmState", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("needs_rescheduling", true)
                    .putString("reschedule_reason", reason)
                    .putString("last_error", e.message)
                    .apply()
            }
        }
    }

    private fun rescheduleAutoBackup(context: Context) {
        try {
            val sharedPreferences = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
            val isBackupEnabled = sharedPreferences.getBoolean("autoBackupEnabled", false)

            if (isBackupEnabled) {
                val hour = sharedPreferences.getInt("backupHour", 2)
                val minute = sharedPreferences.getInt("backupMinute", 0)

                // Assuming you have this method in your AutoBackupReceiver
                AutoBackupReceiver.scheduleBackup(context, hour, minute)
                Log.d("PackageUpdateReceiver", "Rescheduled auto backup after package update")
            }
        } catch (e: Exception) {
            Log.e("PackageUpdateReceiver", "Failed to reschedule auto backup", e)
        }
    }
}