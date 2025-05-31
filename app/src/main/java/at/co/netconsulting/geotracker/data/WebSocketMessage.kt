package at.co.netconsulting.geotracker.data

/**
 * WebSocket message types for multi-user communication
 */
sealed class WebSocketMessage {
    data class RegisterUser(
        val sessionId: String,
        val person: String,
        val eventName: String
    ) : WebSocketMessage()

    data class UnregisterUser(
        val sessionId: String
    ) : WebSocketMessage()

    object GetActiveUsers : WebSocketMessage()

    data class FollowUsers(
        val sessionIds: List<String>
    ) : WebSocketMessage()

    object UnfollowUsers : WebSocketMessage()

    data class ActiveUsersResponse(
        val users: List<ActiveUser>
    ) : WebSocketMessage()

    data class FollowedUserUpdate(
        val point: FollowedUserPoint
    ) : WebSocketMessage()

    data class FollowResponse(
        val following: List<String>,
        val success: Boolean
    ) : WebSocketMessage()

    data class UnfollowResponse(
        val success: Boolean
    ) : WebSocketMessage()
}