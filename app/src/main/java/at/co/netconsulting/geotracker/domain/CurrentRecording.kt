package at.co.netconsulting.geotracker.domain

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for storing the current recording state for recovery after app crashes.
 * This is a temporary table that stores the tracking state during a session.
 */

@Entity(
    tableName = "current_recording",
    indices = [Index(value = ["sessionId"], unique = false)]
)
data class CurrentRecording(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val eventId: Int,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val distance: Double,
    val lap: Int,
    val currentLapDistance: Double,
    val movementDuration: String,
    val inactivityDuration: String,
    val movementStateJson: String = "",
    val lazyStateJson: String = "",
    val startDateTimeEpoch: Long
)