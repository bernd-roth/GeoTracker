package at.co.netconsulting.geotracker.service

import android.content.Context
import android.util.Log
import at.co.netconsulting.geotracker.data.ActiveUser
import at.co.netconsulting.geotracker.data.FollowedUserPoint
import at.co.netconsulting.geotracker.data.FollowedUserLapTime
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
    private var periodicRefreshJob: Job? = null
    private var isConnected = false

    // Configurable trail precision
    private var trailPrecisionMode = TrailPrecisionMode.HIGH_PRECISION

    // State flows for UI
    private val _activeUsers = MutableStateFlow<List<ActiveUser>>(emptyList())
    val activeUsers: StateFlow<List<ActiveUser>> = _activeUsers.asStateFlow()

    private val _followingState = MutableStateFlow(FollowingState())
    val followingState: StateFlow<FollowingState> = _followingState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    enum class TrailPrecisionMode(val description: String, val minDistance: Double) {
        EVERY_POINT("Show every GPS point", 0.0),
        HIGH_PRECISION("High precision (1m)", 1.0),
        MEDIUM_PRECISION("Medium precision (2m)", 2.0),
        LOW_PRECISION("Low precision (5m)", 5.0),
        VERY_LOW_PRECISION("Very low precision (10m)", 10.0)
    }

    companion object {
        private const val TAG = "FollowingService"
        private const val RECONNECT_DELAY = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val PERIODIC_REFRESH_INTERVAL = 60000L // 60 seconds
        private const val MAX_TRAIL_POINTS = 200000000

        @Volatile
        private var INSTANCE: FollowingService? = null

        fun getInstance(context: Context): FollowingService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FollowingService(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    /**
     * Set the trail precision mode
     */
    fun setTrailPrecision(mode: TrailPrecisionMode) {
        trailPrecisionMode = mode
        Log.d(TAG, "Trail precision set to: ${mode.description}")
    }

    /**
     * Get current trail precision mode
     */
    fun getTrailPrecision(): TrailPrecisionMode = trailPrecisionMode

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connection opened for following mode")
            isConnected = true
            _connectionState.value = true

            // Request active users list immediately
            requestActiveUsers()

            // Start periodic refresh
            startPeriodicRefresh()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "WebSocket message received: $text")
            try {
                val json = JSONObject(text)
                val type = json.getString("type")

                when (type) {
                    "active_users" -> {
                        handleActiveUsersResponse(json)
                        _isLoading.value = false
                    }
                    "followed_user_update" -> handleFollowedUserUpdate(json)
                    "follow_response" -> handleFollowResponse(json)
                    "unfollow_response" -> handleUnfollowResponse(json)
                    "update" -> {
                        // Handle regular tracking updates that might affect active users
                        handleTrackingUpdate(json)
                        // Also check if this update is for a followed user and extract weather data
                        handleUpdateForFollowedUsers(json)
                    }
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
            stopPeriodicRefresh()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket connection closed: $code $reason")
            isConnected = false
            _connectionState.value = false
            stopPeriodicRefresh()
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket connection failed", t)
            isConnected = false
            _connectionState.value = false
            stopPeriodicRefresh()
            scheduleReconnect()
        }
    }

    private fun startPeriodicRefresh() {
        stopPeriodicRefresh() // Stop any existing job

        periodicRefreshJob = serviceScope.launch {
            while (isActive && isConnected) {
                delay(PERIODIC_REFRESH_INTERVAL)
                if (isConnected) {
                    Log.d(TAG, "Performing periodic active users refresh")
                    requestActiveUsers()
                }
            }
        }
    }

    private fun stopPeriodicRefresh() {
        periodicRefreshJob?.cancel()
        periodicRefreshJob = null
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
        stopPeriodicRefresh()
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

            val previousCount = _activeUsers.value.size
            _activeUsers.value = users
            _isLoading.value = false

            Log.d(TAG, "Updated active users list: ${users.size} users (was $previousCount)")

            // Log the change details for debugging
            if (users.size != previousCount) {
                val currentSessionIds = users.map { it.sessionId }.toSet()
                val previousSessionIds = _activeUsers.value.map { it.sessionId }.toSet()
                val newUsers = currentSessionIds - previousSessionIds
                val removedUsers = previousSessionIds - currentSessionIds

                if (newUsers.isNotEmpty()) {
                    Log.d(TAG, "New active users detected: $newUsers")
                }
                if (removedUsers.isNotEmpty()) {
                    Log.d(TAG, "Users no longer active: $removedUsers")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing active users response", e)
            _isLoading.value = false
        }
    }

    private fun handleTrackingUpdate(json: JSONObject) {
        try {
            // This handles regular tracking updates that might indicate new active users
            // We don't need to parse the full tracking data, but we can trigger a refresh
            // if we detect this might be a new session

            val point = json.optJSONObject("point")
            if (point != null) {
                val sessionId = point.optString("sessionId", "")
                if (sessionId.isNotEmpty()) {
                    val currentActiveSessionIds = _activeUsers.value.map { it.sessionId }.toSet()
                    if (!currentActiveSessionIds.contains(sessionId)) {
                        Log.d(TAG, "Detected tracking update from unknown session: $sessionId - refreshing active users")
                        // Small delay to allow server to update its active sessions
                        serviceScope.launch {
                            delay(1000)
                            if (isConnected) {
                                requestActiveUsers()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling tracking update", e)
        }
    }

    private fun handleUpdateForFollowedUsers(json: JSONObject) {
        try {
            val point = json.optJSONObject("point")
            if (point != null) {
                val sessionId = point.optString("sessionId", "")
                
                // Check if this update is for a user we're following
                val currentState = _followingState.value
                if (sessionId.isNotEmpty() && sessionId in currentState.followedUsers) {
                    // This is an update for a followed user with full weather data
                    val temperature = if (point.has("temperature")) point.getDouble("temperature") else null
                    val weatherCode = if (point.has("weatherCode")) point.getInt("weatherCode") else null
                    val pressure = if (point.has("pressure")) point.getDouble("pressure") else null
                    val relativeHumidity = if (point.has("relativeHumidity")) point.getInt("relativeHumidity") else null
                    
                    // Handle wind data (could be string or double)
                    val windSpeed = when {
                        point.has("windSpeed") -> {
                            val windSpeedValue = point.get("windSpeed")
                            when (windSpeedValue) {
                                is String -> windSpeedValue.toDoubleOrNull()
                                is Number -> windSpeedValue.toDouble()
                                else -> null
                            }
                        }
                        else -> null
                    }
                    val windDirection = when {
                        point.has("windDirection") -> {
                            val windDirValue = point.get("windDirection")
                            when (windDirValue) {
                                is String -> windDirValue.toDoubleOrNull()
                                is Number -> windDirValue.toDouble()
                                else -> null
                            }
                        }
                        else -> null
                    }

                    Log.d(TAG, "Full weather data from update for ${point.optString("person", "unknown")}: temp=$temperature, code=$weatherCode, pressure=$pressure, humidity=$relativeHumidity, windSpeed=$windSpeed, windDir=$windDirection")

                    val newPoint = FollowedUserPoint(
                        sessionId = sessionId,
                        person = point.getString("person"),
                        latitude = point.getDouble("latitude"),
                        longitude = point.getDouble("longitude"),
                        altitude = point.optDouble("altitude", 0.0),
                        currentSpeed = point.optDouble("currentSpeed", 0.0).toFloat(),
                        distance = point.optDouble("distance", 0.0),
                        heartRate = if (point.has("heartRate") && point.getInt("heartRate") > 0) point.getInt("heartRate") else null,
                        timestamp = point.optString("timestamp", ""),
                        // Weather data from full update message
                        temperature = temperature,
                        weatherCode = weatherCode,
                        pressure = pressure,
                        relativeHumidity = relativeHumidity,
                        windSpeed = windSpeed,
                        windDirection = windDirection
                    )

                    // Update the trail with weather data - reuse the existing trail update logic
                    updateFollowedUserTrail(newPoint)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling update for followed users", e)
        }
    }

    private fun handleFollowedUserUpdate(json: JSONObject) {
        try {
            val pointJson = json.getJSONObject("point")
            // Extract weather data with debug logging - try different field names for wind
            val temperature = if (pointJson.has("temperature")) pointJson.getDouble("temperature") else null
            val weatherCode = if (pointJson.has("weatherCode")) pointJson.getInt("weatherCode") else null
            val pressure = if (pointJson.has("pressure")) pointJson.getDouble("pressure") else null
            val relativeHumidity = if (pointJson.has("relativeHumidity")) pointJson.getInt("relativeHumidity") else null
            
            // Try different possible wind field names
            val windSpeed = when {
                pointJson.has("windSpeed") -> pointJson.getDouble("windSpeed")
                pointJson.has("windspeed") -> pointJson.getDouble("windspeed")
                else -> null
            }
            val windDirection = when {
                pointJson.has("windDirection") -> pointJson.getDouble("windDirection")
                pointJson.has("winddirection") -> pointJson.getDouble("winddirection")
                pointJson.has("windD") -> pointJson.getDouble("windD") // In case it's truncated
                else -> null
            }

            // Parse lap times if they exist
            val lapTimes = if (pointJson.has("lapTimes") && !pointJson.isNull("lapTimes")) {
                val lapTimesArray = pointJson.getJSONArray("lapTimes")
                val lapTimesList = mutableListOf<FollowedUserLapTime>()
                
                for (i in 0 until lapTimesArray.length()) {
                    val lapTimeJson = lapTimesArray.getJSONObject(i)
                    lapTimesList.add(
                        FollowedUserLapTime(
                            lapNumber = lapTimeJson.getInt("lapNumber"),
                            duration = lapTimeJson.getLong("duration"),
                            distance = lapTimeJson.getDouble("distance")
                        )
                    )
                }
                lapTimesList.toList()
            } else null

            // Debug log weather data and lap times
            Log.d(TAG, "Weather data for ${pointJson.optString("person", "unknown")}: temp=$temperature, code=$weatherCode, pressure=$pressure, humidity=$relativeHumidity, windSpeed=$windSpeed, windDir=$windDirection")
            Log.d(TAG, "Lap times for ${pointJson.optString("person", "unknown")}: ${lapTimes?.size ?: 0} lap times received")
            Log.d(TAG, "All JSON keys: ${pointJson.keys().asSequence().toList()}")

            val newPoint = FollowedUserPoint(
                sessionId = pointJson.getString("sessionId"),
                person = pointJson.getString("person"),
                latitude = pointJson.getDouble("latitude"),
                longitude = pointJson.getDouble("longitude"),
                altitude = pointJson.optDouble("altitude", 0.0),
                currentSpeed = pointJson.optDouble("currentSpeed", 0.0).toFloat(),
                distance = pointJson.optDouble("distance", 0.0),
                heartRate = if (pointJson.has("heartRate") && pointJson.getInt("heartRate") > 0) pointJson.getInt("heartRate") else null,
                timestamp = pointJson.optString("timestamp", ""),
                // Weather data
                temperature = temperature,
                weatherCode = weatherCode,
                pressure = pressure,
                relativeHumidity = relativeHumidity,
                windSpeed = windSpeed,
                windDirection = windDirection,
                lapTimes = lapTimes
            )

            // Update the trail
            updateFollowedUserTrail(newPoint)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing followed user update", e)
        }
    }

    private fun updateFollowedUserTrail(newPoint: FollowedUserPoint) {
        try {
            // Update following state by adding the new point to the trail
            val currentState = _followingState.value
            val updatedTrails = currentState.followedUserTrails.toMutableMap()

            val existingTrail = updatedTrails[newPoint.sessionId] ?: emptyList()

            // Check if we should add this point based on current precision mode
            val shouldAddPoint = if (existingTrail.isEmpty()) {
                Log.d(TAG, "First point for ${newPoint.person} - adding to trail")
                true // Always add the first point
            } else if (trailPrecisionMode == TrailPrecisionMode.EVERY_POINT) {
                Log.d(TAG, "EVERY_POINT mode - adding point for ${newPoint.person}")
                true // Add every point when in EVERY_POINT mode
            } else {
                val lastPoint = existingTrail.last()
                val distance = calculateDistance(
                    lastPoint.latitude, lastPoint.longitude,
                    newPoint.latitude, newPoint.longitude
                )
                val shouldAdd = distance >= trailPrecisionMode.minDistance

                if (shouldAdd) {
                    Log.d(TAG, "Distance check passed (${String.format("%.2f", distance)}m >= ${trailPrecisionMode.minDistance}m) - adding point for ${newPoint.person}")
                } else {
                    Log.d(TAG, "Distance check failed (${String.format("%.2f", distance)}m < ${trailPrecisionMode.minDistance}m) - updating existing point for ${newPoint.person}")
                }

                shouldAdd
            }

            if (shouldAddPoint) {
                val updatedTrail = existingTrail + newPoint

                // Limit trail size to prevent memory issues
                val trimmedTrail = if (updatedTrail.size > MAX_TRAIL_POINTS) {
                    updatedTrail.takeLast(MAX_TRAIL_POINTS)
                } else {
                    updatedTrail
                }

                updatedTrails[newPoint.sessionId] = trimmedTrail

                _followingState.value = currentState.copy(
                    followedUserTrails = updatedTrails
                )

                Log.d(TAG, "✅ Added point to trail for ${newPoint.person}. Trail now has ${trimmedTrail.size} points (mode: ${trailPrecisionMode.description})")
            } else {
                // Update just the latest point's data (speed, heart rate, weather, etc.) without adding to trail
                if (existingTrail.isNotEmpty()) {
                    val lastPoint = existingTrail.last()
                    
                    // Preserve existing weather data if the new point doesn't have weather data
                    val mergedPoint = if (newPoint.temperature == null && newPoint.weatherCode == null && 
                                         newPoint.pressure == null && newPoint.relativeHumidity == null && 
                                         newPoint.windSpeed == null && newPoint.windDirection == null &&
                                         (lastPoint.temperature != null || lastPoint.weatherCode != null ||
                                          lastPoint.pressure != null || lastPoint.relativeHumidity != null ||
                                          lastPoint.windSpeed != null || lastPoint.windDirection != null)) {
                        // New point has no weather data but old point does - preserve weather data
                        newPoint.copy(
                            temperature = lastPoint.temperature,
                            weatherCode = lastPoint.weatherCode,
                            pressure = lastPoint.pressure,
                            relativeHumidity = lastPoint.relativeHumidity,
                            windSpeed = lastPoint.windSpeed,
                            windDirection = lastPoint.windDirection
                        )
                    } else {
                        // Use new point as-is (either it has weather data or old point didn't have any)
                        newPoint
                    }
                    
                    val updatedTrail = existingTrail.dropLast(1) + mergedPoint
                    updatedTrails[newPoint.sessionId] = updatedTrail

                    _followingState.value = currentState.copy(
                        followedUserTrails = updatedTrails
                    )

                    Log.d(TAG, "⚠️ Updated latest point data for ${newPoint.person} (trail size: ${updatedTrail.size}) - weather preserved: ${mergedPoint.temperature != null}")
                } else {
                    // This shouldn't happen, but let's handle it
                    updatedTrails[newPoint.sessionId] = listOf(newPoint)
                    _followingState.value = currentState.copy(
                        followedUserTrails = updatedTrails
                    )
                    Log.d(TAG, "🆕 Created new trail for ${newPoint.person} with first point")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating followed user trail", e)
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
                    // Note: Keep existing trails when starting to follow
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

    /**
     * Calculate distance between two points in meters using Haversine formula
     */
    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // Earth radius in meters

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Clear trail for a specific user
     */
    fun clearUserTrail(sessionId: String) {
        val currentState = _followingState.value
        val updatedTrails = currentState.followedUserTrails.toMutableMap()
        updatedTrails.remove(sessionId)

        _followingState.value = currentState.copy(
            followedUserTrails = updatedTrails
        )

        Log.d(TAG, "Cleared trail for user: $sessionId")
    }

    /**
     * Clear all trails
     */
    fun clearAllTrails() {
        _followingState.value = _followingState.value.copy(
            followedUserTrails = emptyMap()
        )
        Log.d(TAG, "Cleared all user trails")
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up FollowingService")
        disconnect()
        serviceScope.cancel()
    }
}