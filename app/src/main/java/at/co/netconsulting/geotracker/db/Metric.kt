package at.co.netconsulting.geotracker.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey

@Entity(
    tableName = "metrics",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Metric(
    @PrimaryKey(autoGenerate = true) val metricId: Int = 0,
    val eventId: Int,
    val heartRate: Int,
    val heartRateDevice: String,
    val speed: Float,
    val distance: Float,
    val cadence: Int?,
    val lap: Int,
    val timeInMilliseconds: Long,
    val unity: String
)