package at.co.netconsulting.geotracker.sync

import android.content.Context
import android.util.Log
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * REST API client for GeoTracker Flask API
 * Handles session CRUD operations via REST API
 */
class GeoTrackerApiClient(private val context: Context) {

    companion object {
        private const val TAG = "GeoTrackerApiClient"
        private const val TIMEOUT_SECONDS = 60L
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private val database = FitnessTrackerDatabase.getInstance(context)

    data class ApiResponse(
        val success: Boolean,
        val message: String? = null,
        val data: JSONObject? = null
    )

    private fun getApiBaseUrl(): String? {
        val sharedPreferences = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        val serverAddress = sharedPreferences.getString("websocketserver", "") ?: ""
        return if (serverAddress.isNotBlank()) {
            "https://$serverAddress/api"
        } else {
            null
        }
    }

    private fun getUserPreferences(): Triple<String, String, String> {
        val sharedPreferences = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        val firstname = sharedPreferences.getString("firstname", "") ?: ""
        val lastname = sharedPreferences.getString("lastname", "") ?: ""
        val birthdate = sharedPreferences.getString("birthdate", "") ?: ""
        return Triple(firstname, lastname, birthdate)
    }

    data class FindSessionResult(
        val found: Boolean,
        val sessionId: String? = null,
        val eventName: String? = null,
        val sportType: String? = null
    )

    // Data classes for download functionality
    data class RemoteSessionSummary(
        val sessionId: String,
        val eventName: String?,
        val sportType: String?,
        val startDateTime: String?,
        val gpsPointCount: Int,
        val startCity: String?,
        val startCountry: String?
    )

    data class FullSessionData(
        val sessionId: String,
        val eventName: String?,
        val sportType: String?,
        val comment: String?,
        val startDateTime: String?,
        val startCity: String?,
        val startCountry: String?,
        val startAddress: String?,
        val endCity: String?,
        val endCountry: String?,
        val endAddress: String?,
        val gpsPoints: List<GpsPointData>,
        val lapTimes: List<LapTimeData>
    )

    data class GpsPointData(
        val latitude: Double,
        val longitude: Double,
        val altitude: Double?,
        val receivedAt: String?,
        val currentSpeed: Float?,
        val distance: Double?,
        val heartRate: Int?,
        val lap: Int,
        val pressure: Float?,
        val pressureAccuracy: Int?,
        val altitudeFromPressure: Float?,
        val seaLevelPressure: Float?,
        val slope: Double?,
        val temperature: Float?,
        val cumulativeElevationGain: Float?
    )

    data class LapTimeData(
        val lapNumber: Int,
        val startTime: Long,
        val endTime: Long,
        val distance: Double
    )

    /**
     * Find a session by date and user info
     */
    suspend fun findSessionByDateAndUser(
        eventDate: String,
        firstname: String,
        lastname: String? = null,
        birthdate: String? = null
    ): Result<FindSessionResult> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getApiBaseUrl()
                ?: return@withContext Result.failure(Exception("Server URL not configured"))

            val urlBuilder = StringBuilder("$baseUrl/sessions/find?date=$eventDate&firstname=$firstname")
            lastname?.let { urlBuilder.append("&lastname=$it") }
            birthdate?.let { urlBuilder.append("&birthdate=$it") }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .build()

            Log.d(TAG, "Finding session by date: $eventDate, user: $firstname")

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val data = json.optJSONObject("data")
                val found = data?.optBoolean("found", false) ?: false
                val sessionId = data?.optString("session_id")?.takeIf { it.isNotBlank() && it != "null" }
                val eventName = data?.optString("event_name")?.takeIf { it.isNotBlank() && it != "null" }
                val sportType = data?.optString("sport_type")?.takeIf { it.isNotBlank() && it != "null" }

                Log.d(TAG, "Find session result: found=$found, sessionId=$sessionId")
                Result.success(FindSessionResult(found, sessionId, eventName, sportType))
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Find session failed: ${response.code} - $errorBody")
                Result.failure(Exception("API error: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error finding session", e)
            Result.failure(e)
        }
    }

    /**
     * Check if a session exists on the remote server
     */
    suspend fun sessionExists(sessionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getApiBaseUrl()
                ?: return@withContext Result.failure(Exception("Server URL not configured"))

            val request = Request.Builder()
                .url("$baseUrl/sessions/$sessionId/exists")
                .get()
                .build()

            Log.d(TAG, "Checking if session exists: $sessionId")

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val exists = json.optJSONObject("data")?.optBoolean("exists", false) ?: false
                Log.d(TAG, "Session $sessionId exists: $exists")
                Result.success(exists)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Session exists check failed: ${response.code} - $errorBody")
                Result.failure(Exception("API error: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking session existence", e)
            Result.failure(e)
        }
    }

    /**
     * Update session metadata on the remote server
     */
    suspend fun updateSession(
        sessionId: String,
        eventName: String?,
        sportType: String?,
        comment: String? = null
    ): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getApiBaseUrl()
                ?: return@withContext Result.failure(Exception("Server URL not configured"))

            val jsonBody = JSONObject().apply {
                eventName?.let { put("event_name", it) }
                sportType?.let { put("sport_type", it) }
                comment?.let { put("comment", it) }
            }

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/sessions/$sessionId")
                .put(requestBody)
                .build()

            Log.d(TAG, "Updating session: $sessionId with data: $jsonBody")

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val success = json.optBoolean("success", false)
                val message = json.optString("message", "Session updated")
                Log.d(TAG, "Session update successful: $message")
                Result.success(ApiResponse(success = success, message = message, data = json.optJSONObject("data")))
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Session update failed: ${response.code} - $errorBody")
                val errorJson = try { JSONObject(errorBody) } catch (e: Exception) { JSONObject() }
                val errorMessage = errorJson.optString("error", "Update failed: ${response.code}")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating session", e)
            Result.failure(e)
        }
    }

    /**
     * Create a new session on the remote server (upload full session with GPS data)
     */
    suspend fun createSession(event: Event): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getApiBaseUrl()
                ?: return@withContext Result.failure(Exception("Server URL not configured"))

            // Gather all data for this event
            val locations = database.locationDao().getLocationsByEventId(event.eventId)
            val metrics = database.metricDao().getMetricsByEventId(event.eventId)
            val lapTimes = database.lapTimeDao().getLapTimesByEvent(event.eventId)

            if (locations.isEmpty() || metrics.isEmpty()) {
                return@withContext Result.failure(Exception("Event has no location or metrics data to upload"))
            }

            Log.d(TAG, "Creating session with ${locations.size} GPS points")

            // Generate sessionId if not present
            val sessionId = event.sessionId ?: generateSessionId(event)

            // Get user preferences
            val (firstname, lastname, birthdate) = getUserPreferences()

            // Build GPS points array
            val gpsPointsArray = JSONArray()
            val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

            locations.forEachIndexed { index, location ->
                val metric = metrics.getOrNull(index)
                val point = JSONObject().apply {
                    put("latitude", location.latitude)
                    put("longitude", location.longitude)
                    put("altitude", location.altitude)
                    put("horizontal_accuracy", 0.0)
                    put("vertical_accuracy_meters", 0.0)
                    put("number_of_satellites", 0)
                    put("used_number_of_satellites", 0)

                    if (metric != null) {
                        val timestamp = timestampFormat.format(Date(metric.timeInMilliseconds))
                        put("received_at", timestamp)
                        put("current_speed", metric.speed)
                        put("average_speed", metric.speed)
                        put("max_speed", metric.speed)
                        put("moving_average_speed", metric.speed)
                        put("speed", metric.speed)
                        put("distance", metric.distance)
                        put("covered_distance", metric.distance)
                        put("cumulative_elevation_gain", metric.elevationGain)
                        put("heart_rate", metric.heartRate)
                        put("lap", metric.lap)
                        put("pressure", metric.pressure ?: 0.0)
                        put("pressure_accuracy", metric.pressureAccuracy ?: 0)
                        put("altitude_from_pressure", metric.altitudeFromPressure ?: 0.0)
                        put("sea_level_pressure", metric.seaLevelPressure ?: 0.0)
                        put("slope", metric.slope)
                        put("average_slope", metric.slope)
                        metric.temperature?.let { put("temperature", it.toDouble()) }
                    } else {
                        put("current_speed", 0.0)
                        put("average_speed", 0.0)
                        put("max_speed", 0.0)
                        put("moving_average_speed", 0.0)
                        put("distance", 0.0)
                        put("lap", 1)
                    }
                }
                gpsPointsArray.put(point)
            }

            // Build lap times array
            val lapTimesArray = JSONArray()
            lapTimes.forEach { lapTime ->
                val lap = JSONObject().apply {
                    put("lap_number", lapTime.lapNumber)
                    put("start_time", lapTime.startTime)
                    put("end_time", lapTime.endTime)
                    put("distance", lapTime.distance)
                }
                lapTimesArray.put(lap)
            }

            // Build the full session JSON
            val jsonBody = JSONObject().apply {
                put("session_id", sessionId)
                put("firstname", firstname)
                put("lastname", lastname)
                put("birthdate", birthdate)
                put("event_name", event.eventName)
                put("sport_type", event.artOfSport)
                put("comment", event.comment)
                put("start_date_time", "${event.eventDate}T00:00:00")
                put("gps_points", gpsPointsArray)
                if (lapTimesArray.length() > 0) {
                    put("lap_times", lapTimesArray)
                }
                // Location geocoding fields
                event.startCity?.let { put("start_city", it) }
                event.startCountry?.let { put("start_country", it) }
                event.startAddress?.let { put("start_address", it) }
                event.endCity?.let { put("end_city", it) }
                event.endCountry?.let { put("end_country", it) }
                event.endAddress?.let { put("end_address", it) }
            }

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/sessions")
                .post(requestBody)
                .build()

            Log.d(TAG, "Creating session: $sessionId with ${gpsPointsArray.length()} GPS points")

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val success = json.optBoolean("success", false)
                val message = json.optString("message", "Session created")
                Log.d(TAG, "Session created successfully: $message")

                // Update local event with sessionId
                database.eventDao().updateEventUploadStatus(
                    event.eventId,
                    sessionId,
                    true,
                    System.currentTimeMillis()
                )

                Result.success(ApiResponse(success = success, message = message, data = json.optJSONObject("data")))
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Session create failed: ${response.code} - $errorBody")
                val errorJson = try { JSONObject(errorBody) } catch (e: Exception) { JSONObject() }
                val errorMessage = errorJson.optString("error", "Create failed: ${response.code}")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating session", e)
            Result.failure(e)
        }
    }

    /**
     * Get session details from the remote server
     */
    suspend fun getSession(sessionId: String): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getApiBaseUrl()
                ?: return@withContext Result.failure(Exception("Server URL not configured"))

            val request = Request.Builder()
                .url("$baseUrl/sessions/$sessionId")
                .get()
                .build()

            Log.d(TAG, "Getting session: $sessionId")

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val success = json.optBoolean("success", false)
                Log.d(TAG, "Session retrieved successfully")
                Result.success(ApiResponse(success = success, data = json.optJSONObject("data")))
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Get session failed: ${response.code} - $errorBody")
                Result.failure(Exception("API error: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting session", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a session from the remote server
     */
    suspend fun deleteSession(sessionId: String): Result<ApiResponse> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getApiBaseUrl()
                ?: return@withContext Result.failure(Exception("Server URL not configured"))

            val request = Request.Builder()
                .url("$baseUrl/sessions/$sessionId")
                .delete()
                .build()

            Log.d(TAG, "Deleting session: $sessionId")

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val success = json.optBoolean("success", false)
                val message = json.optString("message", "Session deleted")
                Log.d(TAG, "Session deleted successfully: $message")
                Result.success(ApiResponse(success = success, message = message))
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Delete session failed: ${response.code} - $errorBody")
                val errorJson = try { JSONObject(errorBody) } catch (e: Exception) { JSONObject() }
                val errorMessage = errorJson.optString("error", "Delete failed: ${response.code}")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting session", e)
            Result.failure(e)
        }
    }

    private fun generateSessionId(event: Event): String {
        val (firstname, _, _) = getUserPreferences()
        val name = firstname.ifBlank { "Unknown" }
        val dateFormatted = event.eventDate.replace("-", "")
        val timeComponent = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
        val randomNumber = (100000..999999).random()
        return "${name}_${dateFormatted}_${timeComponent}_${uuid}_${randomNumber}"
    }

    /**
     * List sessions for a user from the remote server
     */
    suspend fun listUserSessions(
        firstname: String,
        lastname: String? = null,
        birthdate: String? = null
    ): Result<List<RemoteSessionSummary>> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getApiBaseUrl()
                ?: return@withContext Result.failure(Exception("Server URL not configured"))

            val urlBuilder = StringBuilder("$baseUrl/sessions?firstname=$firstname&per_page=100")
            lastname?.let { if (it.isNotBlank()) urlBuilder.append("&lastname=$it") }
            birthdate?.let { if (it.isNotBlank()) urlBuilder.append("&birthdate=$it") }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .build()

            Log.d(TAG, "Listing sessions for user: $firstname")

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val data = json.optJSONObject("data")
                val sessionsArray = data?.optJSONArray("sessions") ?: JSONArray()

                val sessions = mutableListOf<RemoteSessionSummary>()
                for (i in 0 until sessionsArray.length()) {
                    val sessionJson = sessionsArray.getJSONObject(i)
                    sessions.add(
                        RemoteSessionSummary(
                            sessionId = sessionJson.optString("session_id", ""),
                            eventName = sessionJson.optString("event_name")?.takeIf { it.isNotBlank() && it != "null" },
                            sportType = sessionJson.optString("sport_type")?.takeIf { it.isNotBlank() && it != "null" },
                            startDateTime = sessionJson.optString("start_date_time")?.takeIf { it.isNotBlank() && it != "null" },
                            gpsPointCount = sessionJson.optInt("gps_point_count", 0),
                            startCity = sessionJson.optString("start_city")?.takeIf { it.isNotBlank() && it != "null" },
                            startCountry = sessionJson.optString("start_country")?.takeIf { it.isNotBlank() && it != "null" }
                        )
                    )
                }

                Log.d(TAG, "Found ${sessions.size} sessions for user $firstname")
                Result.success(sessions)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "List sessions failed: ${response.code} - $errorBody")
                Result.failure(Exception("API error: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing sessions", e)
            Result.failure(e)
        }
    }

    /**
     * Download full session details including GPS points and lap times
     */
    suspend fun downloadSessionWithDetails(sessionId: String): Result<FullSessionData> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = getApiBaseUrl()
                ?: return@withContext Result.failure(Exception("Server URL not configured"))

            val request = Request.Builder()
                .url("$baseUrl/sessions/$sessionId?include_details=true")
                .get()
                .build()

            Log.d(TAG, "Downloading session details: $sessionId")

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val data = json.optJSONObject("data")
                    ?: return@withContext Result.failure(Exception("No data in response"))

                // Parse GPS points
                val gpsPointsArray = data.optJSONArray("gps_points") ?: JSONArray()
                val gpsPoints = mutableListOf<GpsPointData>()
                for (i in 0 until gpsPointsArray.length()) {
                    val point = gpsPointsArray.getJSONObject(i)
                    gpsPoints.add(
                        GpsPointData(
                            latitude = point.optDouble("latitude", 0.0),
                            longitude = point.optDouble("longitude", 0.0),
                            altitude = point.optDouble("altitude").takeIf { !it.isNaN() },
                            receivedAt = point.optString("received_at")?.takeIf { it.isNotBlank() && it != "null" },
                            currentSpeed = point.optDouble("current_speed").takeIf { !it.isNaN() }?.toFloat(),
                            distance = point.optDouble("distance").takeIf { !it.isNaN() },
                            heartRate = point.optInt("heart_rate", 0).takeIf { it > 0 },
                            lap = point.optInt("lap", 1),
                            pressure = point.optDouble("pressure").takeIf { !it.isNaN() }?.toFloat(),
                            pressureAccuracy = point.optInt("pressure_accuracy", 0).takeIf { it > 0 },
                            altitudeFromPressure = point.optDouble("altitude_from_pressure").takeIf { !it.isNaN() }?.toFloat(),
                            seaLevelPressure = point.optDouble("sea_level_pressure").takeIf { !it.isNaN() }?.toFloat(),
                            slope = point.optDouble("slope").takeIf { !it.isNaN() },
                            temperature = point.optDouble("temperature").takeIf { !it.isNaN() }?.toFloat(),
                            cumulativeElevationGain = point.optDouble("cumulative_elevation_gain").takeIf { !it.isNaN() }?.toFloat()
                        )
                    )
                }

                // Parse lap times
                val lapTimesArray = data.optJSONArray("lap_times") ?: JSONArray()
                val lapTimes = mutableListOf<LapTimeData>()
                for (i in 0 until lapTimesArray.length()) {
                    val lap = lapTimesArray.getJSONObject(i)
                    lapTimes.add(
                        LapTimeData(
                            lapNumber = lap.optInt("lap_number", 0),
                            startTime = lap.optLong("start_time", 0),
                            endTime = lap.optLong("end_time", 0),
                            distance = lap.optDouble("distance", 0.0)
                        )
                    )
                }

                val fullSession = FullSessionData(
                    sessionId = data.optString("session_id", sessionId),
                    eventName = data.optString("event_name")?.takeIf { it.isNotBlank() && it != "null" },
                    sportType = data.optString("sport_type")?.takeIf { it.isNotBlank() && it != "null" },
                    comment = data.optString("comment")?.takeIf { it.isNotBlank() && it != "null" },
                    startDateTime = data.optString("start_date_time")?.takeIf { it.isNotBlank() && it != "null" },
                    startCity = data.optString("start_city")?.takeIf { it.isNotBlank() && it != "null" },
                    startCountry = data.optString("start_country")?.takeIf { it.isNotBlank() && it != "null" },
                    startAddress = data.optString("start_address")?.takeIf { it.isNotBlank() && it != "null" },
                    endCity = data.optString("end_city")?.takeIf { it.isNotBlank() && it != "null" },
                    endCountry = data.optString("end_country")?.takeIf { it.isNotBlank() && it != "null" },
                    endAddress = data.optString("end_address")?.takeIf { it.isNotBlank() && it != "null" },
                    gpsPoints = gpsPoints,
                    lapTimes = lapTimes
                )

                Log.d(TAG, "Downloaded session $sessionId with ${gpsPoints.size} GPS points and ${lapTimes.size} lap times")
                Result.success(fullSession)
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Download session failed: ${response.code} - $errorBody")
                Result.failure(Exception("API error: ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading session", e)
            Result.failure(e)
        }
    }
}
