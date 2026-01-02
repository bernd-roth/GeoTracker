package at.co.netconsulting.geotracker.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Event
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class SessionUploadService(private val context: Context) {
    private val database = FitnessTrackerDatabase.getInstance(context)
    private val gson = Gson()

    companion object {
        private const val TAG = "SessionUploadService"
        private const val TIMEOUT_SECONDS = 60L // Longer timeout for bulk uploads
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
        private const val BATCH_SIZE = 500 // Points per batch to avoid exceeding 1MB frame limit

        private val okHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .build()
        }
    }

    data class UploadResult(
        val success: Boolean,
        val message: String,
        val pointsInserted: Int = 0,
        val pointsSkipped: Int = 0
    )

    private fun getWebsocketServerUrl(): String? {
        val sharedPreferences = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        val serverIp = sharedPreferences.getString("websocketserver", "") ?: ""
        return if (serverIp.isNotBlank()) {
            "wss://$serverIp/geotracker"
        } else {
            null
        }
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking network availability", e)
            true
        }
    }

    /**
     * Get all events that haven't been uploaded yet
     */
    suspend fun getUnuploadedEvents(): List<Event> {
        return database.eventDao().getUnuploadedEvents()
    }

    /**
     * Upload a single event (session) to the WebSocket server
     */
    suspend fun uploadEvent(event: Event): UploadResult {
        if (!isNetworkAvailable()) {
            return UploadResult(
                success = false,
                message = "No network connectivity"
            )
        }

        try {
            // Gather all data for this event
            val locations = database.locationDao().getLocationsByEventId(event.eventId)
            val metrics = database.metricDao().getMetricsByEventId(event.eventId)
            val lapTimes = database.lapTimeDao().getLapTimesByEvent(event.eventId)

            if (locations.isEmpty() || metrics.isEmpty()) {
                return UploadResult(
                    success = false,
                    message = "Event has no location or metrics data to upload"
                )
            }

            Log.d(TAG, "Event has ${locations.size} location points and ${metrics.size} metric points")

            // Build tracking points array - match locations with metrics by index
            val points = JsonArray()
            val sharedPreferences = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)

            locations.forEachIndexed { index, location ->
                val metric = metrics.getOrNull(index) // Match by index
                val point = JsonObject()

                // Basic location data
                point.addProperty("latitude", location.latitude)
                point.addProperty("longitude", location.longitude)
                point.addProperty("altitude", location.altitude)
                // Location entity doesn't store accuracy/satellite data - use defaults
                point.addProperty("accuracy", 0.0)
                point.addProperty("verticalAccuracyMeters", 0.0)
                point.addProperty("numberOfSatellites", 0)
                point.addProperty("usedNumberOfSatellites", 0)

                // Speed and distance data from metric
                if (metric != null) {
                    // Timestamp - format as dd-MM-yyyy HH:mm:ss for server
                    val timestamp = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(metric.timeInMilliseconds))
                    point.addProperty("timestamp", timestamp)

                    point.addProperty("speed", metric.speed)
                    point.addProperty("currentSpeed", metric.speed)
                    point.addProperty("averageSpeed", metric.speed)
                    point.addProperty("maxSpeed", metric.speed)
                    point.addProperty("movingAverageSpeed", metric.speed)
                    point.addProperty("speedAccuracyMetersPerSecond", 0.0)
                    point.addProperty("distance", metric.distance)
                    point.addProperty("coveredDistance", metric.distance)
                    point.addProperty("cumulativeElevationGain", metric.elevationGain)

                    // Heart rate
                    point.addProperty("heartRate", metric.heartRate)
                    point.addProperty("heartRateDevice", metric.heartRateDevice)

                    // Lap
                    point.addProperty("lap", metric.lap)

                    // Barometer data
                    point.addProperty("pressure", metric.pressure ?: 0.0)
                    point.addProperty("pressureAccuracy", metric.pressureAccuracy ?: 0)
                    point.addProperty("altitudeFromPressure", metric.altitudeFromPressure ?: 0.0)
                    point.addProperty("seaLevelPressure", metric.seaLevelPressure ?: 0.0)

                    // Slope data
                    point.addProperty("slope", metric.slope)
                    point.addProperty("averageSlope", metric.slope)
                    point.addProperty("maxUphillSlope", metric.slope)
                    point.addProperty("maxDownhillSlope", 0.0)

                    // Temperature (from metrics)
                    point.addProperty("temperature", metric.temperature ?: 0.0)
                } else {
                    // Default values if no metric found
                    // Use current time as timestamp if metric is missing
                    val timestamp = java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    point.addProperty("timestamp", timestamp)

                    point.addProperty("speed", 0.0)
                    point.addProperty("currentSpeed", 0.0)
                    point.addProperty("averageSpeed", 0.0)
                    point.addProperty("maxSpeed", 0.0)
                    point.addProperty("movingAverageSpeed", 0.0)
                    point.addProperty("speedAccuracyMetersPerSecond", 0.0)
                    point.addProperty("distance", 0.0)
                    point.addProperty("coveredDistance", 0.0)
                    point.addProperty("cumulativeElevationGain", 0.0)
                    point.addProperty("heartRate", 0)
                    point.addProperty("heartRateDevice", "")
                    point.addProperty("lap", 1)
                    point.addProperty("pressure", 0.0)
                    point.addProperty("pressureAccuracy", 0)
                    point.addProperty("altitudeFromPressure", 0.0)
                    point.addProperty("seaLevelPressure", 0.0)
                    point.addProperty("slope", 0.0)
                    point.addProperty("averageSlope", 0.0)
                    point.addProperty("maxUphillSlope", 0.0)
                    point.addProperty("maxDownhillSlope", 0.0)
                    point.addProperty("temperature", 0.0)
                }

                // Weather data (optional - defaults to null/0)
                point.addProperty("windSpeed", 0.0)
                point.addProperty("windDirection", 0.0)
                point.addProperty("relativeHumidity", 0)
                point.addProperty("weatherCode", 0)

                // User info
                point.addProperty("firstname", sharedPreferences.getString("firstname", "") ?: "")
                point.addProperty("lastname", sharedPreferences.getString("lastname", "") ?: "")
                point.addProperty("birthdate", sharedPreferences.getString("birthdate", "") ?: "")

                // Session ID and event info
                point.addProperty("sessionId", event.sessionId ?: generateSessionId(event))
                point.addProperty("eventName", event.eventName)
                point.addProperty("sportType", event.artOfSport)
                point.addProperty("comment", event.comment)

                points.add(point)
            }

            // Add lap times if available
            val lapTimesArray = JsonArray()
            lapTimes.forEach { lapTime ->
                val lapObj = JsonObject()
                lapObj.addProperty("lapNumber", lapTime.lapNumber)
                lapObj.addProperty("startTime", lapTime.startTime)
                lapObj.addProperty("endTime", lapTime.endTime)
                lapObj.addProperty("distance", lapTime.distance)
                // Calculate duration in milliseconds
                val duration = lapTime.endTime - lapTime.startTime
                lapObj.addProperty("duration", duration)
                lapTimesArray.add(lapObj)
            }

            val sessionId = event.sessionId ?: generateSessionId(event)
            Log.d(TAG, "Event sessionId from DB: '${event.sessionId}', using sessionId: '$sessionId'")
            val totalPoints = points.size()

            // Split points into batches to avoid exceeding 1MB frame limit
            val batches = mutableListOf<JsonArray>()
            for (i in 0 until totalPoints step BATCH_SIZE) {
                val batch = JsonArray()
                for (j in i until minOf(i + BATCH_SIZE, totalPoints)) {
                    batch.add(points.get(j))
                }
                batches.add(batch)
            }

            val totalBatches = batches.size
            Log.d(TAG, "Uploading session ${event.eventName} with $totalPoints points in $totalBatches batches")

            var totalPointsInserted = 0
            var totalPointsSkipped = 0

            // Upload each batch
            for (batchIndex in batches.indices) {
                val batch = batches[batchIndex]

                // Create upload request for this batch
                val request = JsonObject()
                request.addProperty("type", "upload_session")
                request.addProperty("sessionId", sessionId)
                request.add("points", batch)
                request.addProperty("batchIndex", batchIndex)
                request.addProperty("totalBatches", totalBatches)

                // Only send lap times with the last batch
                if (batchIndex == totalBatches - 1 && lapTimes.isNotEmpty()) {
                    request.add("lapTimes", lapTimesArray)
                }

                Log.d(TAG, "Uploading batch ${batchIndex + 1}/$totalBatches (${batch.size()} points)")

                // Send with retry logic
                val response = sendWebSocketMessageWithRetry(request)

                if (response == null || response.get("success")?.asBoolean != true) {
                    val errorMsg = response?.get("error")?.asString ?: "Unknown error"
                    return UploadResult(
                        success = false,
                        message = "Failed at batch ${batchIndex + 1}/$totalBatches: $errorMsg"
                    )
                }

                totalPointsInserted += response.get("pointsInserted")?.asInt ?: 0
                totalPointsSkipped += response.get("pointsSkipped")?.asInt ?: 0
            }

            // All batches uploaded successfully - update event status
            database.eventDao().updateEventUploadStatus(
                event.eventId,
                sessionId,
                true,
                System.currentTimeMillis()
            )

            return UploadResult(
                success = true,
                message = "Session uploaded successfully in $totalBatches batches",
                pointsInserted = totalPointsInserted,
                pointsSkipped = totalPointsSkipped
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error uploading event", e)
            return UploadResult(
                success = false,
                message = "Error: ${e.message}"
            )
        }
    }

    private fun generateSessionId(event: Event): String {
        // Generate a session ID matching the format of live recordings
        // Format: firstname_YYYYMMDD_timeComponent_uuid_randomNumber
        val sharedPreferences = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        val firstname = sharedPreferences.getString("firstname", "Unknown") ?: "Unknown"

        // Convert event date from YYYY-MM-DD to YYYYMMDD
        val dateFormatted = event.eventDate.replace("-", "")

        // Time component (milliseconds)
        val timeComponent = System.currentTimeMillis()

        // Generate UUID-like component (first part of UUID without dashes)
        val uuid = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16)

        // Random number component
        val randomNumber = kotlin.random.Random.nextInt(100000, 999999)

        return "${firstname}_${dateFormatted}_${timeComponent}_${uuid}_${randomNumber}"
    }

    private suspend fun sendWebSocketMessageWithRetry(
        requestData: JsonObject,
        maxRetries: Int = MAX_RETRIES
    ): JsonObject? {
        repeat(maxRetries) { attempt ->
            try {
                Log.d(TAG, "Upload attempt ${attempt + 1}/$maxRetries")
                val result = sendWebSocketMessage(requestData)
                if (result != null) {
                    Log.d(TAG, "Upload successful on attempt ${attempt + 1}")
                    return result
                }
            } catch (e: Exception) {
                Log.w(TAG, "Upload attempt ${attempt + 1} failed: ${e.message}", e)
            }

            if (attempt < maxRetries - 1) {
                val delayMs = RETRY_DELAY_MS * (attempt + 1)
                Log.d(TAG, "Waiting ${delayMs}ms before retry...")
                delay(delayMs)
            }
        }

        Log.e(TAG, "All upload attempts failed after $maxRetries tries")
        return null
    }

    private suspend fun sendWebSocketMessage(requestData: JsonObject): JsonObject? {
        val serverUrl = getWebsocketServerUrl()
        if (serverUrl == null) {
            Log.e(TAG, "WebSocket server URL not configured")
            return null
        }

        return withTimeoutOrNull(TIMEOUT_SECONDS * 1000) {
            suspendCancellableCoroutine { continuation ->
                val request = Request.Builder().url(serverUrl).build()
                var webSocket: WebSocket? = null
                var responseReceived = false

                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "WebSocket connected for upload")
                        try {
                            response.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error closing HTTP response", e)
                        }

                        val jsonString = gson.toJson(requestData)
                        val jsonSizeBytes = jsonString.toByteArray(Charsets.UTF_8).size
                        val jsonSizeMB = jsonSizeBytes / (1024.0 * 1024.0)
                        Log.d(TAG, "Sending upload request (size: ${jsonSizeBytes} bytes / ${String.format("%.2f", jsonSizeMB)} MB)")

                        try {
                            if (!webSocket.send(jsonString)) {
                                Log.e(TAG, "Failed to send message")
                                webSocket.close(1000, "Send failed")
                                if (continuation.isActive && !responseReceived) {
                                    responseReceived = true
                                    continuation.resume(null)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception while sending message", e)
                            webSocket.close(1000, "Send exception")
                            if (continuation.isActive && !responseReceived) {
                                responseReceived = true
                                continuation.resume(null)
                            }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (responseReceived) return

                        Log.d(TAG, "Received: $text")
                        try {
                            val response = gson.fromJson(text, JsonObject::class.java)
                            val responseType = response.get("type")?.asString

                            if (responseType == "upload_response") {
                                responseReceived = true
                                webSocket.close(1000, "Upload complete")
                                if (continuation.isActive) {
                                    continuation.resume(response)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing response", e)
                            webSocket.close(1000, "Parse error")
                            if (continuation.isActive && !responseReceived) {
                                responseReceived = true
                                continuation.resume(null)
                            }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        Log.d(TAG, "Received bytes: ${bytes.hex()}")
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closing: $code - $reason")
                        webSocket.close(1000, null)
                        if (continuation.isActive && !responseReceived) {
                            responseReceived = true
                            continuation.resume(null)
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "WebSocket error: ${t.message}", t)
                        response?.close()
                        if (continuation.isActive && !responseReceived) {
                            responseReceived = true
                            continuation.resume(null)
                        }
                    }
                }

                webSocket = okHttpClient.newWebSocket(request, listener)
                continuation.invokeOnCancellation {
                    webSocket?.close(1000, "Cancelled")
                }
            }
        }
    }
}
