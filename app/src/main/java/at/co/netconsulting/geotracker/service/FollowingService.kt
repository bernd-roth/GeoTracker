package at.co.netconsulting.geotracker.service

import android.content.Context
import android.util.Log
import at.co.netconsulting.geotracker.data.ActiveUser
import at.co.netconsulting.geotracker.data.FollowedUserPoint
import at.co.netconsulting.geotracker.data.FollowingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FollowingService private constructor(private val context: Context) {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var isConnected = false

    // State flows for UI
    private val _activeUsers = MutableStateFlow<List<ActiveUser>>(emptyList())
    val activeUsers: StateFlow<List<ActiveUser>> = _activeUsers.asStateFlow()

    private val _followingState = MutableStateFlow(FollowingState())
    val followingState: StateFlow<FollowingState> = _followingState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    companion object {
        private const val TAG = "FollowingService"
        private const val RECONNECT_DELAY = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 10

        @Volatile
        private var INSTANCE: FollowingService? = null

        fun getInstance(context: Context): FollowingService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FollowingService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connection opened for following mode")
            isConnected = true
            _connectionState.value = true
            // Request active users list immediately
            requestActiveUsers()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "WebSocket message received: $text")
            try {
                val json = JSONObject(text)
                val type = json.getString("type")

                when (type) {
                    "active_users" -> handleActiveUsersResponse(json)
                    "followed_user_update" -> handleFollowedUserUpdate(json)
                    "follow_response" -> handleFollowResponse(json)
                    "unfollow_response" -> handleUnfollowResponse(json)
                    // Ignore other message types since we're only following, not recording
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing WebSocket message", e)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket connection closing: $code $reason")
            isConnected = false
            _connectionState.value = false
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket connection closed: $code $reason")
            isConnected = false
            _connectionState.value = false
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket connection failed", t)
            isConnected = false
            _connectionState.value = false
            scheduleReconnect()
        }
    }

    fun connect() {
        if (isConnected) return

        // Get WebSocket URL from SharedPreferences (configured in Settings)
        val sharedPreferences = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        val websocketServer = sharedPreferences.getString("websocketserver", "") ?: ""

        if (websocketServer.isEmpty()) {
            Log.e(TAG, "WebSocket server not configured in settings")
            return
        }

        // Build complete WebSocket URL
        val websocketUrl = "wss://$websocketServer/geotracker"

        Log.d(TAG, "Connecting to WebSocket server for following mode: $websocketUrl")

        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // No timeout for WebSocket
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(websocketUrl)
            .build()

        webSocket = client.newWebSocket(request, webSocketListener)
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting from WebSocket server...")
        reconnectJob?.cancel()
        webSocket?.close(1000, "User stopped following")
        webSocket = null
        isConnected = false
        _connectionState.value = false

        // Reset state
        _activeUsers.value = emptyList()
        _followingState.value = FollowingState()
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return

        reconnectJob = serviceScope.launch {
            repeat(MAX_RECONNECT_ATTEMPTS) { attempt ->
                if (!isActive) return@launch

                Log.d(TAG, "Attempting to reconnect (${attempt + 1}/$MAX_RECONNECT_ATTEMPTS)")
                delay(RECONNECT_DELAY)

                if (!isConnected && isActive) {
                    connect()
                    delay(2000) // Wait a bit to see if connection succeeds
                    if (isConnected) {
                        Log.d(TAG, "Reconnection successful")
                        return@launch
                    }
                } else {
                    return@launch
                }
            }
            Log.e(TAG, "Max reconnection attempts reached")
        }
    }

    fun requestActiveUsers() {
        if (!isConnected) {
            Log.w(TAG, "Cannot request active users - not connected")
            return
        }

        _isLoading.value = true
        val message = """{"type": "get_active_users"}"""
        webSocket?.send(message)
        Log.d(TAG, "Requested active users list")
    }

    fun followUsers(sessionIds: List<String>) {
        if (!isConnected) {
            Log.w(TAG, "Cannot follow users - not connected")
            return
        }

        if (sessionIds.isEmpty()) {
            Log.w(TAG, "Cannot follow users - empty session list")
            return
        }

        val sessionIdsJson = sessionIds.joinToString(",") { "\"$it\"" }
        val message = """{"type": "follow_users", "sessionIds": [$sessionIdsJson]}"""
        webSocket?.send(message)
        Log.d(TAG, "Requested to follow users: $sessionIds")
    }

    fun stopFollowing() {
        if (!isConnected) {
            Log.w(TAG, "Cannot stop following - not connected")
            return
        }

        val message = """{"type": "unfollow_users"}"""
        webSocket?.send(message)
        Log.d(TAG, "Requested to stop following all users")
    }

    private fun handleActiveUsersResponse(json: JSONObject) {
        try {
            val usersArray = json.getJSONArray("users")
            val users = mutableListOf<ActiveUser>()

            for (i in 0 until usersArray.length()) {
                val userJson = usersArray.getJSONObject(i)
                val user = ActiveUser(
                    sessionId = userJson.getString("sessionId"),
                    person = userJson.getString("person"),
                    eventName = userJson.optString("eventName", ""),
                    lastUpdate = userJson.optString("lastUpdate", ""),
                    latitude = userJson.optDouble("latitude", 0.0),
                    longitude = userJson.optDouble("longitude", 0.0)
                )
                users.add(user)
            }

            _activeUsers.value = users
            _isLoading.value = false
            Log.d(TAG, "Updated active users list: ${users.size} users")

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing active users response", e)
            _isLoading.value = false
        }
    }

    private fun handleFollowedUserUpdate(json: JSONObject) {
        try {
            val pointJson = json.getJSONObject("point")
            val point = FollowedUserPoint(
                sessionId = pointJson.getString("sessionId"),
                person = pointJson.getString("person"),
                latitude = pointJson.getDouble("latitude"),
                longitude = pointJson.getDouble("longitude"),
                altitude = pointJson.optDouble("altitude", 0.0),
                currentSpeed = pointJson.optDouble("currentSpeed", 0.0).toFloat(),
                distance = pointJson.optDouble("distance", 0.0),
                heartRate = if (pointJson.has("heartRate")) pointJson.getInt("heartRate") else null,
                timestamp = pointJson.optString("timestamp", "")
            )

            // Update following state with new point
            val currentState = _followingState.value
            val updatedData = currentState.followedUserData.toMutableMap()
            updatedData[point.sessionId] = point

            _followingState.value = currentState.copy(
                followedUserData = updatedData
            )

            Log.d(TAG, "Updated location for followed user: ${point.person}")

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing followed user update", e)
        }
    }

    private fun handleFollowResponse(json: JSONObject) {
        try {
            val success = json.getBoolean("success")
            if (success) {
                val followingArray = json.getJSONArray("following")
                val followedUsers = mutableListOf<String>()

                for (i in 0 until followingArray.length()) {
                    followedUsers.add(followingArray.getString(i))
                }

                _followingState.value = _followingState.value.copy(
                    isFollowing = followedUsers.isNotEmpty(),
                    followedUsers = followedUsers
                )

                Log.d(TAG, "Successfully started following ${followedUsers.size} users")
            } else {
                Log.e(TAG, "Failed to follow users")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing follow response", e)
        }
    }

    private fun handleUnfollowResponse(json: JSONObject) {
        try {
            val success = json.getBoolean("success")
            if (success) {
                _followingState.value = FollowingState()
                Log.d(TAG, "Successfully stopped following all users")
            } else {
                Log.e(TAG, "Failed to stop following users")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing unfollow response", e)
        }
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up FollowingService")
        disconnect()
        serviceScope.cancel()
    }
}