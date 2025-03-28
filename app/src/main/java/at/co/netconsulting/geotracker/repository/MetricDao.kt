package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import at.co.netconsulting.geotracker.data.TimeRange
import at.co.netconsulting.geotracker.domain.Metric

@Dao
interface MetricDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetric(metric: Metric): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetrics(metrics: List<Metric>)

    @Update
    suspend fun updateMetric(metric: Metric)

    @Delete
    suspend fun deleteMetric(metric: Metric)

    @Query("SELECT * FROM metrics WHERE metricId = :metricId")
    suspend fun getMetricById(metricId: Int): Metric?

    @Query("SELECT * FROM metrics WHERE eventId = :eventId ORDER BY timeInMilliseconds ASC")
    suspend fun getMetricsForEvent(eventId: Int): List<Metric>

    @Query("SELECT AVG(speed) FROM metrics WHERE eventId = :eventId")
    suspend fun getAverageSpeedForEvent(eventId: Int): Float?

    @Query("SELECT MAX(speed) FROM metrics WHERE eventId = :eventId")
    suspend fun getMaxSpeedForEvent(eventId: Int): Float?

    @Query("SELECT MIN(timeInMilliseconds) as minTime, MAX(timeInMilliseconds) as maxTime FROM metrics WHERE eventId = :eventId")
    suspend fun getEventTimeRange(eventId: Int): TimeRange?

    @Query("SELECT * FROM metrics WHERE eventId = :eventId ORDER BY metricId ASC")
    suspend fun getMetricsByEventId(eventId: Int): List<Metric>
}