package at.co.netconsulting.geotracker.domain

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing media (photos/videos) attached to events.
 */
@Entity(
    tableName = "event_media",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["eventId"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["eventId"]),
        Index(value = ["mediaUuid"], unique = true)
    ]
)
data class EventMedia(
    @PrimaryKey(autoGenerate = true) val mediaId: Int = 0,
    val eventId: Int,
    val mediaUuid: String,
    val mediaType: String,  // "image" or "video"
    val fileExtension: String,
    val thumbnailUrl: String? = null,  // Server thumbnail URL
    val fullUrl: String? = null,  // Server full media URL
    val localThumbnailPath: String? = null,  // Cached thumbnail on device
    val caption: String? = null,
    val sortOrder: Int = 0,
    val isUploaded: Boolean = false,  // Whether media has been uploaded to server
    val localFilePath: String? = null,  // Local file path before upload
    val fileSizeBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
