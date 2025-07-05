package at.co.netconsulting.geotracker.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "planned_events",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlannedEvent(
    @PrimaryKey(autoGenerate = true) val plannedEventId: Int = 0,
    val userId: Int,
    val plannedEventName: String,
    val plannedEventDate: String, // Store as ISO date (YYYY-MM-DD)
    val plannedEventType: String,
    val plannedEventCountry: String,
    val plannedEventCity: String,
    val plannedLatitude: Double?,
    val plannedLongitude: Double?,
    val isEnteredAndFinished: Boolean = false, // New field for checkbox
    val website: String = "",
    val comment: String = "",
    val reminderDateTime: String = "", // Store as ISO datetime (YYYY-MM-DDTHH:MM:SS)
    val isReminderActive: Boolean = false, // Checkbox to enable/disable reminder
    val isRecurring: Boolean = false,
    val recurringType: String = "", // daily, weekly, monthly, yearly, custom
    val recurringInterval: Int = 1, // Every X days/weeks/months
    val recurringEndDate: String = "", // When to stop recurring (ISO date)
    val recurringDaysOfWeek: String = "" // For weekly: "1,3,5" (Mon, Wed, Fri)
)