package at.co.netconsulting.geotracker.db

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
    val plannedLongitude: Double?
)