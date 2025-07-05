package at.co.netconsulting.geotracker.reminder

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import at.co.netconsulting.geotracker.MainActivity
import at.co.netconsulting.geotracker.R
import kotlinx.coroutines.launch

class ReminderBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("ReminderBroadcastReceiver", "Reminder alarm received")

        val eventId = intent.getIntExtra(ReminderManager.EXTRA_EVENT_ID, -1)
        val eventName = intent.getStringExtra(ReminderManager.EXTRA_EVENT_NAME) ?: "Competition"
        val eventDate = intent.getStringExtra(ReminderManager.EXTRA_EVENT_DATE) ?: ""
        val eventLocation = intent.getStringExtra(ReminderManager.EXTRA_EVENT_LOCATION) ?: ""
        val isRecurring = intent.getBooleanExtra("is_recurring", false)

        if (eventId == -1) {
            Log.e("ReminderBroadcastReceiver", "Invalid event ID received")
            return
        }

        showNotification(context, eventId, eventName, eventDate, eventLocation, isRecurring)

        // If this is a recurring reminder, schedule the next occurrence
        if (isRecurring) {
            scheduleNextRecurrence(context, eventId)
        }
    }

    private fun scheduleNextRecurrence(context: Context, eventId: Int) {
        // Use coroutines to handle database operations
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val database = at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase.getInstance(context)
                val plannedEvent = database.plannedEventDao().getPlannedEventById(eventId)

                if (plannedEvent != null && plannedEvent.isRecurring && plannedEvent.isReminderActive) {
                    val reminderManager = ReminderManager(context)
                    reminderManager.scheduleReminder(plannedEvent)
                    Log.d("ReminderBroadcastReceiver", "Scheduled next recurrence for: ${plannedEvent.plannedEventName}")
                }
            } catch (e: Exception) {
                Log.e("ReminderBroadcastReceiver", "Failed to schedule next recurrence for event ID: $eventId", e)
            }
        }
    }

    private fun showNotification(
        context: Context,
        eventId: Int,
        eventName: String,
        eventDate: String,
        eventLocation: String,
        isRecurring: Boolean = false
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create intent to open the app when notification is tapped
            val appIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                // You can add extras here to navigate directly to competitions tab
                putExtra("navigate_to_competitions", true)
                putExtra("competition_id", eventId)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                eventId,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val recurringText = if (isRecurring) " (Recurring)" else ""
            val bigText = if (isRecurring) {
                "$eventName$recurringText\nDate: $eventDate\nLocation: $eventLocation\n\nThis is a recurring reminder. Next one will be scheduled automatically."
            } else {
                "$eventName\nDate: $eventDate\nLocation: $eventLocation\n\nDon't forget to prepare!"
            }

            // Build the notification
            val notification = NotificationCompat.Builder(context, ReminderManager.NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("Competition Reminder$recurringText")
                .setContentText("$eventName is coming up!")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(bigText)
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            // Show the notification
            notificationManager.notify(eventId, notification)

            Log.d("ReminderBroadcastReceiver", "Notification shown for event: $eventName (recurring: $isRecurring)")

        } catch (e: Exception) {
            Log.e("ReminderBroadcastReceiver", "Failed to show notification for event: $eventName", e)
        }
    }
}