package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import at.co.netconsulting.geotracker.domain.EventMedia
import kotlinx.coroutines.flow.Flow

@Dao
interface EventMediaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: EventMedia): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllMedia(mediaList: List<EventMedia>)

    @Update
    suspend fun updateMedia(media: EventMedia)

    @Delete
    suspend fun deleteMedia(media: EventMedia)

    @Query("SELECT * FROM event_media WHERE mediaId = :mediaId")
    suspend fun getMediaById(mediaId: Int): EventMedia?

    @Query("SELECT * FROM event_media WHERE mediaUuid = :mediaUuid")
    suspend fun getMediaByUuid(mediaUuid: String): EventMedia?

    @Query("SELECT * FROM event_media WHERE eventId = :eventId ORDER BY sortOrder, createdAt")
    suspend fun getMediaForEvent(eventId: Int): List<EventMedia>

    @Query("SELECT * FROM event_media WHERE eventId = :eventId ORDER BY sortOrder, createdAt")
    fun getMediaForEventFlow(eventId: Int): Flow<List<EventMedia>>

    @Query("SELECT COUNT(*) FROM event_media WHERE eventId = :eventId")
    suspend fun getMediaCountForEvent(eventId: Int): Int

    @Query("SELECT * FROM event_media WHERE isUploaded = 0")
    suspend fun getPendingUploads(): List<EventMedia>

    @Query("SELECT * FROM event_media WHERE eventId = :eventId AND isUploaded = 0")
    suspend fun getPendingUploadsForEvent(eventId: Int): List<EventMedia>

    @Query("DELETE FROM event_media WHERE mediaId = :mediaId")
    suspend fun deleteMediaById(mediaId: Int)

    @Query("DELETE FROM event_media WHERE mediaUuid = :mediaUuid")
    suspend fun deleteMediaByUuid(mediaUuid: String)

    @Query("DELETE FROM event_media WHERE eventId = :eventId")
    suspend fun deleteAllMediaForEvent(eventId: Int)

    @Query("""
        UPDATE event_media
        SET isUploaded = :isUploaded,
            thumbnailUrl = :thumbnailUrl,
            fullUrl = :fullUrl
        WHERE mediaId = :mediaId
    """)
    suspend fun updateMediaUploadStatus(
        mediaId: Int,
        isUploaded: Boolean,
        thumbnailUrl: String?,
        fullUrl: String?
    )

    @Query("UPDATE event_media SET localThumbnailPath = :localPath WHERE mediaId = :mediaId")
    suspend fun updateLocalThumbnailPath(mediaId: Int, localPath: String?)

    @Query("UPDATE event_media SET caption = :caption WHERE mediaId = :mediaId")
    suspend fun updateCaption(mediaId: Int, caption: String?)

    @Query("UPDATE event_media SET sortOrder = :sortOrder WHERE mediaId = :mediaId")
    suspend fun updateSortOrder(mediaId: Int, sortOrder: Int)
}
