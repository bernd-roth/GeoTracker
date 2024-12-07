package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.data.RoutePoint
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

    @Query("""
    SELECT DISTINCT 
        e.eventId, 
        e.eventName, 
        e.eventDate, 
        e.artOfSport, 
        e.comment,
        m.metricId, 
        m.heartRate, 
        m.heartRateDevice, 
        m.speed, 
        m.distance,
        m.cadence, 
        m.lap, 
        m.timeInMilliseconds, 
        m.unity,
        m.elevation,
        m.elevationGain,
        m.elevationLoss
    FROM events e
    LEFT JOIN (
        SELECT eventId, metricId, heartRate, heartRateDevice, speed, distance,
               cadence, lap, timeInMilliseconds, unity, elevation, elevationGain, elevationLoss
        FROM metrics
        WHERE (eventId, distance) IN (
            SELECT eventId, MAX(distance)
            FROM metrics
            GROUP BY eventId
        )
    ) m ON e.eventId = m.eventId
    GROUP BY e.eventId
    ORDER BY e.eventDate DESC
""")
    suspend fun getDetailsFromEventJoinedOnMetricsWithRecordingData(): List<SingleEventWithMetric>

    @Query("SELECT e.userId, e.eventId, e.eventDate, e.eventName , e.artOfSport, e.comment FROM events e")
    suspend fun getAllEvents(): List<Event>

    @Query("DELETE FROM events WHERE eventId = :eventId")
    suspend fun delete(eventId: Int)

    @Query("DELETE FROM events")
    suspend fun deleteAllContent()

    @Query("""
        SELECT DISTINCT l.eventId, l.latitude, l.longitude
        FROM locations l
        WHERE l.eventId = :eventId 
        AND l.latitude != 0.0 
        AND l.longitude != 0.0
        ORDER BY l.locationId
    """)
    suspend fun getRoutePointsForEvent(eventId: Int): List<RoutePoint>
}