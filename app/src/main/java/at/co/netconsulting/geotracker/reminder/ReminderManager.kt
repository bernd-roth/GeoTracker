package at.co.netconsulting.geotracker.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import at.co.netconsulting.geotracker.domain.PlannedEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReminderManager(private val context: Context) {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "competition_reminders"
        const val NOTIFICATION_CHANNEL_NAME = "Competition Reminders"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_EVENT_NAME = "event_name"
        const val EXTRA_EVENT_DATE = "event_date"
        const val EXTRA_EVENT_LOCATION = "event_location"
    }

    private val alarmManager: AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for upcoming competition reminders"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d("ReminderManager", "Notification channel created")
        }
    }

    fun scheduleReminder(plannedEvent: PlannedEvent) {
        if (!plannedEvent.isReminderActive || plannedEvent.reminderDateTime.isEmpty()) {
            Log.d("ReminderManager", "Reminder not active or no reminder time set for event: ${plannedEvent.plannedEventName}")
            return
        }

        try {
            if (plannedEvent.isRecurring) {
                scheduleRecurringReminder(plannedEvent)
            } else {
                scheduleSingleReminder(plannedEvent)
            }
        } catch (e: Exception) {
            Log.e("ReminderManager", "Failed to schedule reminder for event: ${plannedEvent.plannedEventName}", e)
        }
    }

    private fun scheduleSingleReminder(plannedEvent: PlannedEvent) {
        val reminderTime = parseReminderDateTime(plannedEvent.reminderDateTime)
        val currentTime = System.currentTimeMillis()

        if (reminderTime <= currentTime) {
            Log.d("ReminderManager", "Reminder time is in the past for event: ${plannedEvent.plannedEventName}")
            return
        }

        scheduleAlarmAt(plannedEvent, reminderTime, isRecurring = false)
    }

    private fun scheduleRecurringReminder(plannedEvent: PlannedEvent) {
        val nextReminderTime = calculateNextReminderTime(plannedEvent)
        if (nextReminderTime != null && nextReminderTime > System.currentTimeMillis()) {
            scheduleAlarmAt(plannedEvent, nextReminderTime, isRecurring = true)
            Log.d("ReminderManager", "Scheduled recurring reminder for ${plannedEvent.plannedEventName} at ${Date(nextReminderTime)}")
        } else {
            Log.d("ReminderManager", "No future recurring reminder times for event: ${plannedEvent.plannedEventName}")
        }
    }

    private fun scheduleAlarmAt(plannedEvent: PlannedEvent, triggerTime: Long, isRecurring: Boolean) {
        val intent = Intent(context, ReminderBroadcastReceiver::class.java).apply {
            putExtra(EXTRA_EVENT_ID, plannedEvent.plannedEventId)
            putExtra(EXTRA_EVENT_NAME, plannedEvent.plannedEventName)
            putExtra(EXTRA_EVENT_DATE, plannedEvent.plannedEventDate)
            putExtra(EXTRA_EVENT_LOCATION, "${plannedEvent.plannedEventCity}, ${plannedEvent.plannedEventCountry}")
            putExtra("is_recurring", isRecurring)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            plannedEvent.plannedEventId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setExactAndAllowWhileIdle for more reliable delivery
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    fun calculateNextReminderTime(plannedEvent: PlannedEvent): Long? {
        if (!plannedEvent.isRecurring || plannedEvent.reminderDateTime.isEmpty()) return null

        val baseTime = parseReminderDateTime(plannedEvent.reminderDateTime)
        if (baseTime == 0L) return null

        val calendar = Calendar.getInstance().apply { timeInMillis = baseTime }
        val currentTime = System.currentTimeMillis()
        val endTime = if (plannedEvent.recurringEndDate.isNotEmpty()) {
            parseEndDate(plannedEvent.recurringEndDate)
        } else Long.MAX_VALUE

        // If base time is in the future, use it
        if (baseTime > currentTime) {
            return if (baseTime <= endTime) baseTime else null
        }

        // Calculate next occurrence based on recurring type
        when (plannedEvent.recurringType) {
            "daily" -> {
                while (calendar.timeInMillis <= currentTime) {
                    calendar.add(Calendar.DAY_OF_MONTH, plannedEvent.recurringInterval)
                }
            }
            "weekly" -> {
                if (plannedEvent.recurringDaysOfWeek.isNotEmpty()) {
                    return calculateNextWeeklyReminder(plannedEvent, currentTime, endTime)
                } else {
                    while (calendar.timeInMillis <= currentTime) {
                        calendar.add(Calendar.WEEK_OF_YEAR, plannedEvent.recurringInterval)
                    }
                }
            }
            "monthly" -> {
                while (calendar.timeInMillis <= currentTime) {
                    calendar.add(Calendar.MONTH, plannedEvent.recurringInterval)
                }
            }
            "yearly" -> {
                while (calendar.timeInMillis <= currentTime) {
                    calendar.add(Calendar.YEAR, plannedEvent.recurringInterval)
                }
            }
            else -> return null
        }

        return if (calendar.timeInMillis <= endTime) calendar.timeInMillis else null
    }

    private fun calculateNextWeeklyReminder(plannedEvent: PlannedEvent, currentTime: Long, endTime: Long): Long? {
        val daysOfWeek = plannedEvent.recurringDaysOfWeek.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .sorted()

        if (daysOfWeek.isEmpty()) return null

        val baseCalendar = Calendar.getInstance().apply {
            timeInMillis = parseReminderDateTime(plannedEvent.reminderDateTime)
        }
        val baseHour = baseCalendar.get(Calendar.HOUR_OF_DAY)
        val baseMinute = baseCalendar.get(Calendar.MINUTE)

        val currentCalendar = Calendar.getInstance().apply { timeInMillis = currentTime }
        val currentDayOfWeek = currentCalendar.get(Calendar.DAY_OF_WEEK)

        // Try to find next occurrence in current week
        for (dayOfWeek in daysOfWeek) {
            if (dayOfWeek >= currentDayOfWeek) {
                val testCalendar = Calendar.getInstance().apply {
                    timeInMillis = currentTime
                    set(Calendar.DAY_OF_WEEK, dayOfWeek)
                    set(Calendar.HOUR_OF_DAY, baseHour)
                    set(Calendar.MINUTE, baseMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                if (testCalendar.timeInMillis > currentTime && testCalendar.timeInMillis <= endTime) {
                    return testCalendar.timeInMillis
                }
            }
        }

        // If no occurrence in current week, find first occurrence in next eligible week
        currentCalendar.add(Calendar.WEEK_OF_YEAR, plannedEvent.recurringInterval)
        currentCalendar.set(Calendar.DAY_OF_WEEK, daysOfWeek.first())
        currentCalendar.set(Calendar.HOUR_OF_DAY, baseHour)
        currentCalendar.set(Calendar.MINUTE, baseMinute)
        currentCalendar.set(Calendar.SECOND, 0)
        currentCalendar.set(Calendar.MILLISECOND, 0)

        return if (currentCalendar.timeInMillis <= endTime) currentCalendar.timeInMillis else null
    }

    private fun parseEndDate(endDate: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = format.parse(endDate)
            date?.time ?: Long.MAX_VALUE
        } catch (e: Exception) {
            Log.e("ReminderManager", "Failed to parse end date: $endDate", e)
            Long.MAX_VALUE
        }
    }

    fun cancelReminder(eventId: Int) {
        try {
            val intent = Intent(context, ReminderBroadcastReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                eventId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()

            Log.d("ReminderManager", "Reminder cancelled for event ID: $eventId")

        } catch (e: Exception) {
            Log.e("ReminderManager", "Failed to cancel reminder for event ID: $eventId", e)
        }
    }

    fun updateReminder(plannedEvent: PlannedEvent) {
        // Cancel existing reminder first
        cancelReminder(plannedEvent.plannedEventId)

        // Schedule new reminder if active
        if (plannedEvent.isReminderActive) {
            scheduleReminder(plannedEvent)
        }
    }

    private fun parseReminderDateTime(reminderDateTime: String): Long {
        return try {
            // Parse ISO datetime format: YYYY-MM-DDTHH:MM:SS
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = format.parse(reminderDateTime)
            date?.time ?: 0L
        } catch (e: Exception) {
            Log.e("ReminderManager", "Failed to parse reminder date time: $reminderDateTime", e)
            0L
        }
    }

    fun formatReminderDateTime(calendar: Calendar): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        return format.format(calendar.time)
    }

    fun parseReminderDateTimeToCalendar(reminderDateTime: String): Calendar? {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = format.parse(reminderDateTime)
            if (date != null) {
                Calendar.getInstance().apply {
                    time = date
                }
            } else null
        } catch (e: Exception) {
            Log.e("ReminderManager", "Failed to parse reminder date time to calendar: $reminderDateTime", e)
            null
        }
    }
}