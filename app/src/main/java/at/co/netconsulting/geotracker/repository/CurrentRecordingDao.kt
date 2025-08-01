package at.co.netconsulting.geotracker.repository

import androidx.room.*
import at.co.netconsulting.geotracker.domain.CurrentRecording

@Dao
interface CurrentRecordingDao {

    @Query("SELECT * FROM current_recording ORDER BY timestamp ASC")
    suspend fun getAllCurrentRecordings(): List<CurrentRecording>

    @Query("SELECT * FROM current_recording WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getAllRecordsForSession(sessionId: String): List<CurrentRecording>

    @Query("SELECT * FROM current_recording WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestRecordForSession(sessionId: String): CurrentRecording?

    @Query("SELECT * FROM current_recording WHERE eventId = :eventId ORDER BY timestamp ASC")
    suspend fun getRecordsByEvent(eventId: Int): List<CurrentRecording>

    @Query("SELECT * FROM current_recording WHERE id = :id")
    suspend fun getCurrentRecordingById(id: Int): CurrentRecording?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrentRecord(currentRecording: CurrentRecording): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrentRecords(currentRecordings: List<CurrentRecording>): List<Long>

    @Update
    suspend fun updateCurrentRecording(currentRecording: CurrentRecording)

    @Delete
    suspend fun deleteCurrentRecording(currentRecording: CurrentRecording)

    @Query("DELETE FROM current_recording WHERE sessionId = :sessionId")
    suspend fun clearSessionRecords(sessionId: String)

    @Query("SELECT COUNT(*) FROM current_recording WHERE sessionId = :sessionId")
    suspend fun getRecordCountForSession(sessionId: String): Int

    @Query("DELETE FROM current_recording WHERE eventId = :eventId")
    suspend fun deleteRecordsByEvent(eventId: Int)

    @Query("DELETE FROM current_recording")
    suspend fun deleteAllCurrentRecordings()

    @Query("SELECT COUNT(*) FROM current_recording")
    suspend fun getCurrentRecordingsCount(): Int

    @Query("SELECT COUNT(*) FROM current_recording WHERE sessionId = :sessionId")
    suspend fun getRecordsCountBySession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM current_recording WHERE eventId = :eventId")
    suspend fun getRecordsCountByEvent(eventId: Int): Int

    @Query("SELECT DISTINCT sessionId FROM current_recording")
    suspend fun getAllSessionIds(): List<String>

    @Query("SELECT * FROM current_recording WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getRecordsByTimeRange(startTime: Long, endTime: Long): List<CurrentRecording>
}