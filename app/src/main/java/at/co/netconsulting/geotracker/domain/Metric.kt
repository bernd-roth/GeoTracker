
package at.co.netconsulting.geotracker.domain

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
    val distance: Double,
    val cadence: Int?,
    val lap: Int,
    val timeInMilliseconds: Long,
    val unity: String,
    val elevation: Float = 0f,
    val elevationGain: Float = 0f,
    val elevationLoss: Float = 0f,
    val steps: Int? = null,                // Step count (from phone sensors or manual calculation)
    val strideLength: Float? = null,       // Calculated from speed/cadence
    val temperature: Float? = null,        // From weather API
    val accuracy: Float? = null            // GPS accuracy in meters
)