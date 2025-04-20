package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.domain.LapTime

/**
 * DAO for LapTime entity.
 * Provides methods to save and retrieve lap timing information.
 */
@Dao
interface LapTimeDao {
    @Insert
    suspend fun insertLapTime(lapTime: LapTime): Long

    @Query("SELECT * FROM lap_times WHERE sessionId = :sessionId ORDER BY lapNumber ASC")
    suspend fun getLapTimesForSession(sessionId: String): List<LapTime>

    @Query("SELECT * FROM lap_times WHERE eventId = :eventId ORDER BY lapNumber ASC")
    suspend fun getLapTimesForEvent(eventId: Int): List<LapTime>

    @Query("SELECT MAX(lapNumber) FROM lap_times WHERE sessionId = :sessionId")
    suspend fun getMaxLapNumberForSession(sessionId: String): Int?

    @Query("SELECT * FROM lap_times WHERE sessionId = :sessionId AND lapNumber = :lapNumber LIMIT 1")
    suspend fun getLapTimeByNumber(sessionId: String, lapNumber: Int): LapTime?
}