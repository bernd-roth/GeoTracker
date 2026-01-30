package at.co.netconsulting.geotracker.domain

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "discipline_transitions",
    indices = [
        Index(value = ["eventId"]),
        Index(value = ["sessionId"])
    ]
)
data class DisciplineTransition(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val eventId: Int,
    val sessionId: String,
    val disciplineName: String,
    val transitionNumber: Int,
    val timestamp: Long
)
