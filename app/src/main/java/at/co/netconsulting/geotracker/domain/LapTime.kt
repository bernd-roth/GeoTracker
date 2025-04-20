package at.co.netconsulting.geotracker.domain

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for storing lap time information.
 * This tracks each completed lap with timing information.
 */
@Entity(tableName = "lap_times")
data class LapTime(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val eventId: Int,
    val lapNumber: Int,
    val startTime: Long,
    val endTime: Long,
    val distance: Double
)