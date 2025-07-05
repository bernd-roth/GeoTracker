package at.co.netconsulting.geotracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.reminder.ReminderManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            intent.action == Intent.ACTION_PACKAGE_REPLACED) {

            Log.d("BootReceiver", "Device booted or app updated, rescheduling reminders")

            // Reschedule all active reminders
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = FitnessTrackerDatabase.getInstance(context)
                    val reminderManager = ReminderManager(context)

                    // Get current user ID (you might need to adjust this based on your user management)
                    val currentUserId = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                        .getInt("current_user_id", 1)

                    val plannedEvents = database.plannedEventDao().getPlannedEventsForUser(currentUserId)

                    plannedEvents.forEach { event ->
                        if (event.isReminderActive && event.reminderDateTime.isNotEmpty()) {
                            reminderManager.scheduleReminder(event)
                            val reminderType = if (event.isRecurring) "recurring" else "single"
                            Log.d("BootReceiver", "Rescheduled $reminderType reminder for: ${event.plannedEventName}")
                        }
                    }

                    val recurringCount = plannedEvents.count { it.isRecurring && it.isReminderActive }
                    val singleCount = plannedEvents.count { !it.isRecurring && it.isReminderActive }
                    Log.d("BootReceiver", "Finished rescheduling $recurringCount recurring and $singleCount single reminders")

                } catch (e: Exception) {
                    Log.e("BootReceiver", "Failed to reschedule reminders", e)
                }
            }
        }
    }
}