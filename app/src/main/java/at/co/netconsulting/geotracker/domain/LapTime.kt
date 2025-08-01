package at.co.netconsulting.geotracker.domain

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entity for storing lap time information.
 * This tracks each completed lap with timing information.
 */
@Entity(
    tableName = "lap_times",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["eventId"])
    ]
)
data class LapTime(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sessionId: String,
    val eventId: Int,
    val lapNumber: Int,
    val startTime: Long,
    val endTime: Long,
    val distance: Double
)