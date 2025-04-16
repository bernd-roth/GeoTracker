package at.co.netconsulting.geotracker.repository

/**
 * Data class for storing geographic bounds of a path
 */
data class PathBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
) {
    /**
     * Expands this bounding box by a buffer percentage in all directions
     * @param bufferPercent Buffer to add as a percentage (0.1 = 10%)
     * @return Expanded bounding box
     */
    fun expand(bufferPercent: Double): PathBounds {
        val latDelta = (maxLat - minLat) * bufferPercent
        val lonDelta = (maxLon - minLon) * bufferPercent

        return PathBounds(
            minLat = minLat - latDelta,
            maxLat = maxLat + latDelta,
            minLon = minLon - lonDelta,
            maxLon = maxLon + lonDelta
        )
    }

    /**
     * Checks if this bounding box contains another one
     */
    fun contains(other: PathBounds): Boolean {
        return minLat <= other.minLat &&
                maxLat >= other.maxLat &&
                minLon <= other.minLon &&
                maxLon >= other.maxLon
    }

    /**
     * Checks if this bounding box overlaps with another one
     */
    fun overlaps(other: PathBounds): Boolean {
        return !(other.minLat > maxLat ||
                other.maxLat < minLat ||
                other.minLon > maxLon ||
                other.maxLon < minLon)
    }
}