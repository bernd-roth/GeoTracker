package at.co.netconsulting.geotracker.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.PlannedEvent
import at.co.netconsulting.geotracker.reminder.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class PlannedEventsNetworkManager(private val context: Context) {
    private val database = FitnessTrackerDatabase.getInstance(context)
    private val reminderManager = ReminderManager(context)
    private val gson = Gson()

    companion object {
        private const val TAG = "PlannedEventsNetwork"
        private const val TIMEOUT_SECONDS = 30L
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L

        // ✅ Singleton OkHttpClient for connection reuse and pooling
        private val okHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                // ✅ Configure connection pool for better connection reuse
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                // Note: Removed retry interceptor as it interferes with WebSocket upgrades
                // WebSocket retry logic is handled at the application level instead
                .build()
        }
    }

    data class NetworkResult(
        val success: Boolean,
        val message: String,
        val uploadedCount: Int = 0,
        val duplicateCount: Int = 0,
        val errorCount: Int = 0,
        val downloadedCount: Int = 0,
        val errors: List<String> = emptyList()
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

    private fun getUserInfo(): Triple<String, String, String> {
        val sharedPreferences = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        return Triple(
            sharedPreferences.getString("firstname", "") ?: "",
            sharedPreferences.getString("lastname", "") ?: "",
            sharedPreferences.getString("birthdate", "") ?: ""
        )
    }

    // ✅ Check network connectivity before attempting connections
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking network availability", e)
            true // Assume network is available if we can't check
        }
    }

    // ✅ Enhanced WebSocket message sending with comprehensive retry logic
    private suspend fun sendWebSocketMessageWithRetry(
        requestData: JsonObject,
        expectedResponseType: String,
        maxRetries: Int = MAX_RETRIES
    ): JsonObject? {
        if (!isNetworkAvailable()) {
            Log.e(TAG, "No network connectivity available")
            return null
        }

        repeat(maxRetries) { attempt ->
            try {
                Log.d(TAG, "WebSocket attempt ${attempt + 1}/$maxRetries - Network: ${isNetworkAvailable()}, Thread: ${Thread.currentThread().name}")

                val result = sendWebSocketMessage(requestData, expectedResponseType)
                if (result != null) {
                    Log.d(TAG, "WebSocket successful on attempt ${attempt + 1}")
                    return result
                }
                Log.w(TAG, "WebSocket attempt ${attempt + 1} returned null response")

            } catch (e: Exception) {
                Log.w(TAG, "WebSocket attempt ${attempt + 1} failed: ${e.message}", e)

                // Don't retry on certain unrecoverable errors
                when {
                    e.message?.contains("404") == true -> {
                        Log.e(TAG, "Server endpoint not found (404), not retrying")
                        return null
                    }
                    e.message?.contains("401") == true || e.message?.contains("403") == true -> {
                        Log.e(TAG, "Authentication error, not retrying")
                        return null
                    }
                }
            }

            // Apply exponential backoff before retry (except on last attempt)
            if (attempt < maxRetries - 1) {
                val delayMs = RETRY_DELAY_MS * (attempt + 1)
                Log.d(TAG, "Waiting ${delayMs}ms before retry...")
                delay(delayMs)
            }
        }

        Log.e(TAG, "All WebSocket attempts failed after $maxRetries tries")
        return null
    }

    private suspend fun sendWebSocketMessage(requestData: JsonObject, expectedResponseType: String): JsonObject? {
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
                        Log.d(TAG, "WebSocket connected for planned events sync")

                        // ✅ Properly close the HTTP response to prevent resource leaks
                        try {
                            response.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error closing HTTP response", e)
                        }

                        val jsonString = gson.toJson(requestData)
                        Log.d(TAG, "Sending: $jsonString")

                        // ✅ Check if connection is still active before sending
                        try {
                            if (!webSocket.send(jsonString)) {
                                Log.e(TAG, "Failed to send message - WebSocket might be closed")
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
                        if (responseReceived) {
                            Log.d(TAG, "Ignoring additional message (response already received)")
                            return
                        }

                        Log.d(TAG, "Received: $text")
                        try {
                            val response = gson.fromJson(text, JsonObject::class.java)
                            val responseType = response.get("type")?.asString

                            if (responseType == expectedResponseType) {
                                responseReceived = true
                                webSocket.close(1000, "Complete")
                                if (continuation.isActive) {
                                    continuation.resume(response)
                                }
                            } else {
                                Log.d(TAG, "Received different message type: $responseType, waiting for: $expectedResponseType")
                                // Continue waiting for the expected response type
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing response", e)
                            responseReceived = true
                            webSocket.close(1000, "Parse error")
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        onMessage(webSocket, bytes.utf8())
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closing: $code - $reason")
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        Log.d(TAG, "WebSocket closed: $code - $reason")
                        if (continuation.isActive && !responseReceived) {
                            responseReceived = true
                            continuation.resume(null)
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "WebSocket error: ${t.message}", t)

                        // ✅ Close the response if provided to prevent resource leaks
                        response?.close()

                        if (continuation.isActive && !responseReceived) {
                            responseReceived = true
                            continuation.resume(null)
                        }
                    }
                }

                try {
                    // ✅ Use singleton client for better connection reuse
                    webSocket = okHttpClient.newWebSocket(request, listener)
                } catch (e: Exception) {
                    Log.e(TAG, "Error creating WebSocket", e)
                    if (continuation.isActive && !responseReceived) {
                        responseReceived = true
                        continuation.resume(null)
                    }
                    return@suspendCancellableCoroutine
                }

                continuation.invokeOnCancellation {
                    responseReceived = true
                    webSocket?.close(1000, "Cancelled")
                }
            }
        }
    }

    suspend fun uploadPlannedEvents(): NetworkResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting upload of planned events")

            val (firstname, lastname, birthdate) = getUserInfo()
            if (firstname.isBlank()) {
                Log.e(TAG, "User information not configured for upload")
                return@withContext NetworkResult(false, "User information not configured")
            }

            // Get current user ID
            val currentUserId = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                .getInt("current_user_id", 1)

            // Get all planned events for current user
            val plannedEvents = database.plannedEventDao().getAllPlannedEventsForUser(currentUserId)
            Log.d(TAG, "Found ${plannedEvents.size} planned events to upload for user $currentUserId")

            if (plannedEvents.isEmpty()) {
                return@withContext NetworkResult(false, "No planned events to upload")
            }

            // Convert to JSON using Gson
            val eventsArray = JsonArray()
            plannedEvents.forEach { event ->
                val eventJson = JsonObject().apply {
                    addProperty("plannedEventName", event.plannedEventName)
                    addProperty("plannedEventDate", event.plannedEventDate)
                    addProperty("plannedEventType", event.plannedEventType)
                    addProperty("plannedEventCountry", event.plannedEventCountry)
                    addProperty("plannedEventCity", event.plannedEventCity)
                    if (event.plannedLatitude != null) addProperty("plannedLatitude", event.plannedLatitude)
                    if (event.plannedLongitude != null) addProperty("plannedLongitude", event.plannedLongitude)
                    addProperty("isEnteredAndFinished", event.isEnteredAndFinished)
                    addProperty("website", event.website)
                    addProperty("comment", event.comment)
                    addProperty("reminderDateTime", event.reminderDateTime)
                    addProperty("isReminderActive", event.isReminderActive)
                    addProperty("isRecurring", event.isRecurring)
                    addProperty("recurringType", event.recurringType)
                    addProperty("recurringInterval", event.recurringInterval)
                    addProperty("recurringEndDate", event.recurringEndDate)
                    addProperty("recurringDaysOfWeek", event.recurringDaysOfWeek)
                }
                eventsArray.add(eventJson)
            }

            val requestJson = JsonObject().apply {
                addProperty("type", "upload_planned_events")
                add("events", eventsArray)
                addProperty("userFirstname", firstname)
                addProperty("userLastname", lastname)
                addProperty("userBirthdate", birthdate)
            }

            // ✅ Use retry logic for better reliability
            val response = sendWebSocketMessageWithRetry(requestJson, "upload_planned_events_response")

            if (response != null && response.get("success")?.asBoolean == true) {
                val errors = mutableListOf<String>()
                response.get("errors")?.asJsonArray?.forEach { error ->
                    errors.add(error.asString)
                }

                val result = NetworkResult(
                    success = true,
                    message = "Events uploaded successfully",
                    uploadedCount = response.get("uploaded_count")?.asInt ?: 0,
                    duplicateCount = response.get("duplicate_count")?.asInt ?: 0,
                    errorCount = response.get("error_count")?.asInt ?: 0,
                    errors = errors
                )

                Log.d(TAG, "Upload successful: ${result.uploadedCount} uploaded, ${result.duplicateCount} duplicates, ${result.errorCount} errors")
                return@withContext result

            } else {
                val errorMessage = response?.get("error")?.asString ?: "Upload failed - no response from server after retries"
                Log.e(TAG, "Upload failed: $errorMessage")
                return@withContext NetworkResult(false, errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading planned events", e)
            return@withContext NetworkResult(false, "Upload failed: ${e.message}")
        }
    }

    suspend fun downloadPlannedEvents(): NetworkResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting download of planned events")

            val (firstname, lastname, birthdate) = getUserInfo()
            if (firstname.isBlank()) {
                Log.e(TAG, "User information not configured for download")
                return@withContext NetworkResult(false, "User information not configured")
            }

            val currentUserId = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                .getInt("current_user_id", 1)

            val requestJson = JsonObject().apply {
                addProperty("type", "download_planned_events")
                addProperty("userFirstname", firstname)
                addProperty("userLastname", lastname)
                addProperty("userBirthdate", birthdate)
                addProperty("excludeUserEvents", true) // Don't download user's own events
            }

            // ✅ Use retry logic for better reliability
            val response = sendWebSocketMessageWithRetry(requestJson, "download_planned_events_response")

            if (response != null && response.get("success")?.asBoolean == true) {
                val eventsArray = response.get("events")?.asJsonArray
                var savedCount = 0
                var duplicateCount = 0
                val errors = mutableListOf<String>()

                Log.d(TAG, "Processing ${eventsArray?.size() ?: 0} downloaded events")

                eventsArray?.forEach { eventElement ->
                    try {
                        val eventJson = eventElement.asJsonObject

                        // Check if event already exists locally (avoid duplicates)
                        val eventName = eventJson.get("plannedEventName").asString
                        val eventDate = eventJson.get("plannedEventDate").asString
                        val eventCountry = eventJson.get("plannedEventCountry").asString
                        val eventCity = eventJson.get("plannedEventCity").asString

                        val existingEvents = database.plannedEventDao().searchPlannedEvents(
                            currentUserId,
                            eventName
                        ).filter { existing ->
                            existing.plannedEventName == eventName &&
                                    existing.plannedEventDate == eventDate &&
                                    existing.plannedEventCountry == eventCountry &&
                                    existing.plannedEventCity == eventCity
                        }

                        if (existingEvents.isNotEmpty()) {
                            duplicateCount++
                            Log.d(TAG, "Skipping duplicate event: $eventName")
                            return@forEach
                        }

                        // Create new PlannedEvent
                        val plannedEvent = PlannedEvent(
                            userId = currentUserId,
                            plannedEventName = eventName,
                            plannedEventDate = eventDate,
                            plannedEventType = eventJson.get("plannedEventType")?.asString ?: "",
                            plannedEventCountry = eventCountry,
                            plannedEventCity = eventCity,
                            plannedLatitude = eventJson.get("plannedLatitude")?.let {
                                if (it.isJsonNull) null else it.asDouble
                            },
                            plannedLongitude = eventJson.get("plannedLongitude")?.let {
                                if (it.isJsonNull) null else it.asDouble
                            },
                            isEnteredAndFinished = eventJson.get("isEnteredAndFinished")?.asBoolean ?: false,
                            website = eventJson.get("website")?.asString ?: "",
                            comment = eventJson.get("comment")?.asString ?: "",
                            reminderDateTime = eventJson.get("reminderDateTime")?.asString ?: "",
                            isReminderActive = eventJson.get("isReminderActive")?.asBoolean ?: false,
                            isRecurring = eventJson.get("isRecurring")?.asBoolean ?: false,
                            recurringType = eventJson.get("recurringType")?.asString ?: "",
                            recurringInterval = eventJson.get("recurringInterval")?.asInt ?: 1,
                            recurringEndDate = eventJson.get("recurringEndDate")?.asString ?: "",
                            recurringDaysOfWeek = eventJson.get("recurringDaysOfWeek")?.asString ?: ""
                        )

                        // Save to local database
                        val eventId = database.plannedEventDao().insertPlannedEvent(plannedEvent).toInt()
                        Log.d(TAG, "Saved downloaded event: $eventName with ID: $eventId")

                        // Set up reminder if active
                        if (plannedEvent.isReminderActive && plannedEvent.reminderDateTime.isNotEmpty()) {
                            try {
                                val savedEvent = plannedEvent.copy(plannedEventId = eventId)
                                reminderManager.updateReminder(savedEvent)
                                Log.d(TAG, "Set up reminder for downloaded event: ${plannedEvent.plannedEventName}")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error setting up reminder for downloaded event", e)
                                errors.add("Could not set reminder for ${plannedEvent.plannedEventName}: ${e.message}")
                            }
                        }

                        savedCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing downloaded event", e)
                        errors.add("Error processing event: ${e.message}")
                    }
                }

                val result = NetworkResult(
                    success = true,
                    message = "Events downloaded successfully",
                    downloadedCount = savedCount,
                    duplicateCount = duplicateCount,
                    errors = errors
                )

                Log.d(TAG, "Download successful: ${result.downloadedCount} new events, ${result.duplicateCount} duplicates")
                return@withContext result

            } else {
                val errorMessage = response?.get("error")?.asString ?: "Download failed - no response from server after retries"
                Log.e(TAG, "Download failed: $errorMessage")
                return@withContext NetworkResult(false, errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading planned events", e)
            return@withContext NetworkResult(false, "Download failed: ${e.message}")
        }
    }

    /**
     * Test the planned events sync connection with enhanced retry logic
     */
    suspend fun testPlannedEventsConnection(): NetworkResult = withContext(Dispatchers.IO) {
        val websocketserver = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
            .getString("websocketserver", "") ?: ""

        if (websocketserver.isBlank()) {
            Log.e(TAG, "WebSocket server not configured")
            return@withContext NetworkResult(false, "WebSocket server not configured")
        }

        Log.d(TAG, "Testing planned events connection to: wss://$websocketserver:6789")

        return@withContext try {
            // Create test request
            val testRequest = JsonObject().apply {
                addProperty("type", "ping")
                addProperty("message", "planned_events_test")
            }

            // ✅ Use retry logic for connection test (fewer retries for test)
            val response = sendWebSocketMessageWithRetry(testRequest, "pong", maxRetries = 2)

            if (response != null) {
                val message = response.get("message")?.asString ?: "Connection successful"
                Log.d(TAG, "Connection test successful: $message")
                NetworkResult(true, "Connection test successful: $message")
            } else {
                Log.e(TAG, "Connection test failed - no response from server")
                NetworkResult(false, "No response from server after retries")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error testing connection", e)
            NetworkResult(false, "Connection test failed: ${e.message}")
        }
    }

    /**
     * ✅ Add connection cleanup method for proper resource management
     */
    fun cleanup() {
        try {
            Log.d(TAG, "Cleaning up WebSocket resources")
            okHttpClient.dispatcher.executorService.shutdown()
            okHttpClient.connectionPool.evictAll()
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        }
    }

    /**
     * ✅ Get connection statistics for debugging
     */
    fun getConnectionStats(): String {
        return try {
            val pool = okHttpClient.connectionPool
            "Connections - Idle: ${pool.idleConnectionCount()}, Total: ${pool.connectionCount()}"
        } catch (e: Exception) {
            "Connection stats unavailable: ${e.message}"
        }
    }
}