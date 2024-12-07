package at.co.netconsulting.geotracker.repository

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import at.co.netconsulting.geotracker.data.MetricDebug
import at.co.netconsulting.geotracker.domain.Metric

@Dao
interface MetricDao {
    @Insert
    suspend fun insertMetric(metric: Metric): Long

    @Query("SELECT * FROM metrics WHERE eventId = :eventId ORDER BY timeInMilliseconds DESC LIMIT 1")
    suspend fun getLastMetricByEvent(eventId: Int): Metric?

/*    @Query("SELECT * FROM metrics")
    suspend fun getAllMetrics(): List<Metric>*/

    @Insert
    suspend fun insertAll(metrics: List<Metric>)

    @Query("""
    SELECT metricId, timeInMilliseconds, distance
    FROM metrics 
    WHERE eventId = :eventId 
    ORDER BY timeInMilliseconds ASC
""")
    suspend fun getMetricsForEvent(eventId: Int): List<MetricDebug>
}