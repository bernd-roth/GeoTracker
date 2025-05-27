package at.co.netconsulting.geotracker.data

/**
 * Data class for storing time range information from metrics
 * Used by MetricDao.getEventTimeRange()
 */
data class TimeRange(
    val minTime: Long,
    val maxTime: Long
) {
    /**
     * Get the duration in milliseconds
     */
    fun getDurationMs(): Long = maxTime - minTime

    /**
     * Get the duration in seconds
     */
    fun getDurationSeconds(): Long = getDurationMs() / 1000

    /**
     * Get the duration in minutes
     */
    fun getDurationMinutes(): Double = getDurationSeconds() / 60.0

    /**
     * Get the duration in hours
     */
    fun getDurationHours(): Double = getDurationMinutes() / 60.0

    /**
     * Get duration formatted as HH:MM:SS
     */
    fun getFormattedDuration(): String {
        val totalSeconds = getDurationSeconds()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    /**
     * Check if a timestamp falls within this range
     */
    fun contains(timestamp: Long): Boolean {
        return timestamp >= minTime && timestamp <= maxTime
    }

    /**
     * Check if this range overlaps with another
     */
    fun overlaps(other: TimeRange): Boolean {
        return !(other.minTime > maxTime || other.maxTime < minTime)
    }

    /**
     * Expand this time range by a buffer in milliseconds
     */
    fun expand(bufferMs: Long): TimeRange {
        return TimeRange(
            minTime = minTime - bufferMs,
            maxTime = maxTime + bufferMs
        )
    }
}