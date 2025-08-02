package at.co.netconsulting.geotracker.data

import androidx.compose.runtime.Immutable

/**
 * Represents the current following state
 */
@Immutable
data class FollowingState(
    val isFollowing: Boolean = false,
    val followedUsers: List<String> = emptyList(),
    // Changed to store a list of points per user to create trails
    val followedUserTrails: Map<String, List<FollowedUserPoint>> = emptyMap()
) {
    /**
     * Get the current (latest) position for a user
     */
    fun getCurrentPosition(sessionId: String): FollowedUserPoint? {
        return followedUserTrails[sessionId]?.lastOrNull()
    }

    /**
     * Get the starting position for a user
     */
    fun getStartPosition(sessionId: String): FollowedUserPoint? {
        return followedUserTrails[sessionId]?.firstOrNull()
    }

    /**
     * Get the complete trail for a user
     */
    fun getTrail(sessionId: String): List<FollowedUserPoint> {
        return followedUserTrails[sessionId] ?: emptyList()
    }

    /**
     * Check if we have enough points to draw a trail (at least 2 points)
     */
    fun hasTrail(sessionId: String): Boolean {
        return (followedUserTrails[sessionId]?.size ?: 0) >= 2
    }

    /**
     * Get all users that have trails
     */
    fun getUsersWithTrails(): List<String> {
        return followedUserTrails.filter { it.value.size >= 2 }.keys.toList()
    }
}