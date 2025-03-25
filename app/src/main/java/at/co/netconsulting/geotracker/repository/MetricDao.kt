package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.domain.Metric

@Dao
interface MetricDao {
    @Insert
    suspend fun insertMetric(metric: Metric): Long

    @Query("SELECT * FROM metrics WHERE eventId = :eventId ORDER BY metricId ASC")
    suspend fun getMetricsByEventId(eventId: Int): List<Metric>
}