package at.co.netconsulting.geotracker.data

/**
 * Data class to represent the bounds of a geographic path
 * Used for efficiently loading paths within the current viewport
 */
data class PathBounds(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double
) {
    /**
     * Expands the bounds by a percentage in all directions
     * This creates a buffer zone around the current bounds
     * @param percent The percentage to expand by (0.1 = 10%)
     * @return A new expanded PathBounds
     */
    fun expand(percent: Double): PathBounds {
        val latDiff = maxLat - minLat
        val lonDiff = maxLon - minLon

        val latExpand = latDiff * percent
        val lonExpand = lonDiff * percent

        return PathBounds(
            minLat = minLat - latExpand,
            maxLat = maxLat + latExpand,
            minLon = minLon - lonExpand,
            maxLon = maxLon + lonExpand
        )
    }

    /**
     * Checks if a point is contained within these bounds
     * @param lat The latitude
     * @param lon The longitude
     * @return True if the point is inside the bounds
     */
    fun contains(lat: Double, lon: Double): Boolean {
        return lat in minLat..maxLat && lon in minLon..maxLon
    }

    /**
     * Checks if another PathBounds overlaps with this one
     * @param other The other PathBounds to check against
     * @return True if the bounds overlap
     */
    fun overlaps(other: PathBounds): Boolean {
        // Check if one rectangle is to the left of the other
        if (maxLon < other.minLon || other.maxLon < minLon) {
            return false
        }

        // Check if one rectangle is above the other
        if (maxLat < other.minLat || other.maxLat < minLat) {
            return false
        }

        // The rectangles overlap
        return true
    }
}