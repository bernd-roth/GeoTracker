package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import at.co.netconsulting.geotracker.domain.DisciplineTransition

@Dao
interface DisciplineTransitionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransition(transition: DisciplineTransition): Long

    @Query("SELECT * FROM discipline_transitions WHERE eventId = :eventId ORDER BY transitionNumber ASC")
    suspend fun getTransitionsForEvent(eventId: Int): List<DisciplineTransition>

    @Query("SELECT * FROM discipline_transitions WHERE sessionId = :sessionId ORDER BY transitionNumber ASC")
    suspend fun getTransitionsForSession(sessionId: String): List<DisciplineTransition>

    @Query("SELECT * FROM discipline_transitions WHERE eventId = :eventId ORDER BY transitionNumber DESC LIMIT 1")
    suspend fun getLatestTransitionForEvent(eventId: Int): DisciplineTransition?

    @Query("DELETE FROM discipline_transitions WHERE eventId = :eventId")
    suspend fun deleteTransitionsByEvent(eventId: Int)

    @Query("DELETE FROM discipline_transitions WHERE sessionId = :sessionId")
    suspend fun deleteTransitionsBySession(sessionId: String)
}
