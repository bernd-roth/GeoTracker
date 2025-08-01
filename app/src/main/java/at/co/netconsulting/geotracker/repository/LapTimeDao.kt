package at.co.netconsulting.geotracker.repository

import androidx.room.*
import at.co.netconsulting.geotracker.domain.LapTime

@Dao
interface LapTimeDao {

    @Query("SELECT * FROM lap_times ORDER BY id ASC")
    suspend fun getAllLapTimes(): List<LapTime>

    @Query("SELECT * FROM lap_times WHERE sessionId = :sessionId ORDER BY lapNumber ASC")
    suspend fun getLapTimesBySession(sessionId: String): List<LapTime>

    @Query("SELECT * FROM lap_times WHERE sessionId = :sessionId ORDER BY lapNumber ASC")
    suspend fun getLapTimesForSession(sessionId: String): List<LapTime>

    @Query("SELECT * FROM lap_times WHERE eventId = :eventId ORDER BY lapNumber ASC")
    suspend fun getLapTimesForEvent(eventId: Int): List<LapTime>

    @Query("SELECT * FROM lap_times WHERE eventId = :eventId ORDER BY lapNumber ASC")
    suspend fun getLapTimesByEvent(eventId: Int): List<LapTime>

    @Query("SELECT * FROM lap_times WHERE id = :id")
    suspend fun getLapTimeById(id: Int): LapTime?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLapTime(lapTime: LapTime): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLapTimes(lapTimes: List<LapTime>): List<Long>

    @Update
    suspend fun updateLapTime(lapTime: LapTime)

    @Delete
    suspend fun deleteLapTime(lapTime: LapTime)

    @Query("DELETE FROM lap_times WHERE sessionId = :sessionId")
    suspend fun deleteLapTimesBySession(sessionId: String)

    @Query("DELETE FROM lap_times WHERE eventId = :eventId")
    suspend fun deleteLapTimesByEvent(eventId: Int)

    @Query("DELETE FROM lap_times")
    suspend fun deleteAllLapTimes()

    @Query("SELECT COUNT(*) FROM lap_times")
    suspend fun getLapTimesCount(): Int

    @Query("SELECT COUNT(*) FROM lap_times WHERE sessionId = :sessionId")
    suspend fun getLapTimesCountBySession(sessionId: String): Int
}