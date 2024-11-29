package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.data.SingleEventWithMetric
import at.co.netconsulting.geotracker.domain.Event

@Dao
interface EventDao {
    @Insert
    suspend fun insertEvent(event: Event): Long

    @Query("SELECT * FROM events WHERE userId = :userId ORDER BY eventDate DESC LIMIT 1")
    suspend fun getLastEventByUser(userId: Int): Event?

    @Query("SELECT eventId FROM events WHERE userId = :userId ORDER BY eventDate DESC LIMIT 1")
    suspend fun getLastEventIdByUser(userId: Int): Int

    @Query("SELECT eventId, eventName, eventDate, userId, artOfSport, comment FROM events GROUP BY eventDate ORDER BY eventDate")
    suspend fun getEventDateEventNameGroupByEventDate(): List<Event>

    @Query("SELECT e.eventId, e.eventName, e.eventDate, e.artOfSport, e.comment, " +
            "m.metricId, m.heartRate, m.heartRateDevice, m.speed, m.distance, " +
            "m.cadence, m.lap, m.timeInMilliseconds, m.unity " +
            "FROM events e " +
            "INNER JOIN metrics m ON e.eventId = m.eventId " +
            "WHERE m.timeInMilliseconds = (SELECT MAX(timeInMilliseconds) FROM metrics WHERE eventId = e.eventId) " +
            "ORDER BY m.timeInMilliseconds")
    suspend fun getDetailsFromEventJoinedOnMetricsWithRecordingData(): List<SingleEventWithMetric>

    @Query("SELECT e.userId, e.eventId, e.eventDate, e.eventName , e.artOfSport, e.comment FROM events e")
    suspend fun getAllEvents(): List<Event>

    @Query("DELETE FROM events WHERE eventId = :eventId")
    suspend fun delete(eventId: Int)

    @Query("DELETE FROM events")
    suspend fun deleteAllContent()
}