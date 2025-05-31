package at.co.netconsulting.geotracker.data

import androidx.compose.runtime.Immutable

/**
 * Represents the current following state
 */
@Immutable
data class FollowingState(
    val isFollowing: Boolean = false,
    val followedUsers: List<String> = emptyList(),
    val followedUserData: Map<String, FollowedUserPoint> = emptyMap()
)