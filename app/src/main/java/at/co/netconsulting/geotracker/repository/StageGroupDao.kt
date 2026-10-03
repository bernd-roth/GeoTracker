package at.co.netconsulting.geotracker.repository

import androidx.room.*
import at.co.netconsulting.geotracker.domain.*
import kotlinx.coroutines.flow.Flow

data class StageSummary(
    @Embedded val event: Event,
    val groupName: String,
    val distanceMeters: Double,
    val durationMillis: Long
)

@Dao
abstract class StageGroupDao {
    @Query("SELECT * FROM stage_groups ORDER BY name COLLATE NOCASE")
    abstract fun observeGroups(): Flow<List<StageGroup>>

    @Query("SELECT stageGroupId FROM stage_groups WHERE name = :name COLLATE NOCASE LIMIT 1")
    abstract suspend fun findByName(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(group: StageGroup): Long

    @Transaction
    open suspend fun resolve(name: String): Long? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        return findByName(trimmed) ?: insert(StageGroup(name = trimmed))
    }

    @Query("SELECT COALESCE(MAX(stageOrder), 0) + 1 FROM events WHERE stageGroupId = :groupId")
    abstract suspend fun nextOrder(groupId: Long): Int

    @Query("""SELECT e.*, g.name AS groupName,
        COALESCE(m.distanceMeters, 0.0) AS distanceMeters,
        COALESCE(m.durationMillis, 0) AS durationMillis
        FROM events e JOIN stage_groups g ON g.stageGroupId = e.stageGroupId
        LEFT JOIN (SELECT eventId, MAX(distance) AS distanceMeters,
            MAX(timeInMilliseconds) - MIN(timeInMilliseconds) AS durationMillis
            FROM metrics GROUP BY eventId) m ON m.eventId = e.eventId
        ORDER BY e.stageOrder, e.eventDate, e.eventId""")
    abstract fun observeStages(): Flow<List<StageSummary>>

    @Query("SELECT * FROM events WHERE stageGroupId = :groupId ORDER BY stageOrder, eventDate, eventId")
    abstract suspend fun members(groupId: Long): List<Event>

    @Query("UPDATE events SET stageGroupId = NULL, stageOrder = NULL WHERE stageGroupId = :groupId")
    abstract suspend fun detachMembers(groupId: Long)

    @Query("DELETE FROM stage_groups WHERE stageGroupId = :groupId")
    abstract suspend fun deleteEmptyGroup(groupId: Long)

    @Transaction
    open suspend fun removeGroup(groupId: Long) {
        detachMembers(groupId)
        deleteEmptyGroup(groupId)
    }
}
