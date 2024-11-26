package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.data.RecordingData
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

    @Query("SELECT e.eventDate, e.eventName, MAX(m.distance) AS distance, m.speed FROM events e INNER JOIN metrics m ON e.eventId = m.eventId GROUP BY e.eventDate ORDER BY e.eventDate")
    suspend fun getDetailsFromEventJoinedOnMetricsWithRecordingData(): List<RecordingData>
}