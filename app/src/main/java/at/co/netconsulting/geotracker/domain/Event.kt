package at.co.netconsulting.geotracker.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["userId"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Event(
    @PrimaryKey(autoGenerate = true) val eventId: Int = 0,
    val userId: Long,
    val eventName: String,
    val eventDate: String, // Store as ISO date (YYYY-MM-DD)
    val artOfSport: String,
    val comment: String,
    val sessionId: String? = null, // Server session ID for uploaded events
    val isUploaded: Boolean = false, // Track if event has been uploaded to server
    val uploadedAt: Long? = null // Timestamp when event was uploaded (epoch millis)
)