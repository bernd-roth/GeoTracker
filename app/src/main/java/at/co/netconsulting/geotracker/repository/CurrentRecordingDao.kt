package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.domain.CurrentRecording

/**
 * DAO for CurrentRecording entity.
 * Provides methods to save, retrieve, and clear temporary tracking state.
 */
@Dao
interface CurrentRecordingDao {
    @Insert
    suspend fun insertCurrentRecord(record: CurrentRecording): Long

    @Query("SELECT * FROM current_recording WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecordForSession(sessionId: String): CurrentRecording?

    @Query("SELECT * FROM current_recording WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getAllRecordsForSession(sessionId: String): List<CurrentRecording>

    @Query("DELETE FROM current_recording WHERE sessionId = :sessionId")
    suspend fun clearSessionRecords(sessionId: String)

    @Query("SELECT COUNT(*) FROM current_recording WHERE sessionId = :sessionId")
    suspend fun getRecordCountForSession(sessionId: String): Int
}