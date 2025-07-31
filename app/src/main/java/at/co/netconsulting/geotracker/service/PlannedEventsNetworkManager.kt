package at.co.netconsulting.geotracker.service

import android.content.Context
import android.util.Log
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.PlannedEvent
import at.co.netconsulting.geotracker.reminder.ReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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

    private fun createOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun sendWebSocketMessage(requestData: JsonObject, expectedResponseType: String): JsonObject? {
        val serverUrl = getWebsocketServerUrl()
        if (serverUrl == null) {
            Log.e(TAG, "WebSocket server URL not configured")
            return null
        }

        return withTimeoutOrNull(TIMEOUT_SECONDS * 1000) {
            suspendCancellableCoroutine { continuation ->
                val client = createOkHttpClient()
                val request = Request.Builder().url(serverUrl).build()
                var webSocket: WebSocket? = null

                val listener = object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        Log.d(TAG, "WebSocket connected for planned events sync")
                        val jsonString = gson.toJson(requestData)
                        Log.d(TAG, "Sending: $jsonString")
                        webSocket.send(jsonString)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        Log.d(TAG, "Received: $text")
                        try {
                            val response = gson.fromJson(text, JsonObject::class.java)
                            val responseType = response.get("type")?.asString

                            if (responseType == expectedResponseType) {
                                webSocket.close(1000, "Complete")
                                if (continuation.isActive) {
                                    continuation.resume(response)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing response", e)
                            webSocket.close(1000, "Error")
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
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.e(TAG, "WebSocket error", t)
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
                }

                webSocket = client.newWebSocket(request, listener)

                continuation.invokeOnCancellation {
                    webSocket?.close(1000, "Cancelled")
                }
            }
        }
    }

    suspend fun uploadPlannedEvents(): NetworkResult = withContext(Dispatchers.IO) {
        try {
            val (firstname, lastname, birthdate) = getUserInfo()
            if (firstname.isBlank()) {
                return@withContext NetworkResult(false, "User information not configured")
            }

            // Get current user ID
            val currentUserId = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                .getInt("current_user_id", 1)

            // Get all planned events for current user
            val plannedEvents = database.plannedEventDao().getAllPlannedEventsForUser(currentUserId)

            if (plannedEvents.isEmpty()) {
                return@withContext NetworkResult(false, "No planned events to upload")
            }

            // Convert to JSON using Gson like your existing code
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

            val response = sendWebSocketMessage(requestJson, "upload_planned_events_response")

            if (response != null && response.get("success")?.asBoolean == true) {
                val errors = mutableListOf<String>()
                response.get("errors")?.asJsonArray?.forEach { error ->
                    errors.add(error.asString)
                }

                return@withContext NetworkResult(
                    success = true,
                    message = "Events uploaded successfully",
                    uploadedCount = response.get("uploaded_count")?.asInt ?: 0,
                    duplicateCount = response.get("duplicate_count")?.asInt ?: 0,
                    errorCount = response.get("error_count")?.asInt ?: 0,
                    errors = errors
                )
            } else {
                val errorMessage = response?.get("error")?.asString ?: "Upload failed - no response from server"
                return@withContext NetworkResult(false, errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading planned events", e)
            return@withContext NetworkResult(false, "Upload failed: ${e.message}")
        }
    }

    suspend fun downloadPlannedEvents(): NetworkResult = withContext(Dispatchers.IO) {
        try {
            val (firstname, lastname, birthdate) = getUserInfo()
            if (firstname.isBlank()) {
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

            val response = sendWebSocketMessage(requestJson, "download_planned_events_response")

            if (response != null && response.get("success")?.asBoolean == true) {
                val eventsArray = response.get("events")?.asJsonArray
                var savedCount = 0
                var duplicateCount = 0
                val errors = mutableListOf<String>()

                eventsArray?.forEach { eventElement ->
                    try {
                        val eventJson = eventElement.asJsonObject

                        // Check if event already exists locally (avoid duplicates)
                        val eventName = eventJson.get("plannedEventName").asString
                        val existingEvents = database.plannedEventDao().searchPlannedEvents(
                            currentUserId,
                            eventName
                        ).filter { existing ->
                            existing.plannedEventName == eventName &&
                                    existing.plannedEventDate == eventJson.get("plannedEventDate").asString &&
                                    existing.plannedEventCountry == eventJson.get("plannedEventCountry").asString &&
                                    existing.plannedEventCity == eventJson.get("plannedEventCity").asString
                        }

                        if (existingEvents.isNotEmpty()) {
                            duplicateCount++
                            return@forEach
                        }

                        // Create new PlannedEvent
                        val plannedEvent = PlannedEvent(
                            userId = currentUserId,
                            plannedEventName = eventName,
                            plannedEventDate = eventJson.get("plannedEventDate").asString,
                            plannedEventType = eventJson.get("plannedEventType")?.asString ?: "",
                            plannedEventCountry = eventJson.get("plannedEventCountry").asString,
                            plannedEventCity = eventJson.get("plannedEventCity").asString,
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

                        // Set up reminder if active
                        if (plannedEvent.isReminderActive && plannedEvent.reminderDateTime.isNotEmpty()) {
                            val savedEvent = plannedEvent.copy(plannedEventId = eventId)
                            reminderManager.updateReminder(savedEvent)
                            Log.d(TAG, "Set up reminder for downloaded event: ${plannedEvent.plannedEventName}")
                        }

                        savedCount++
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing downloaded event", e)
                        errors.add("Error processing event: ${e.message}")
                    }
                }

                return@withContext NetworkResult(
                    success = true,
                    message = "Events downloaded successfully",
                    downloadedCount = savedCount,
                    duplicateCount = duplicateCount,
                    errors = errors
                )
            } else {
                val errorMessage = response?.get("error")?.asString ?: "Download failed - no response from server"
                return@withContext NetworkResult(false, errorMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading planned events", e)
            return@withContext NetworkResult(false, "Download failed: ${e.message}")
        }
    }

    /**
     * Test the planned events sync connection (similar to your sendTestDataToWebSocketServer)
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

            val response = sendWebSocketMessage(testRequest, "pong")

            if (response != null) {
                NetworkResult(true, "Connection test successful")
            } else {
                NetworkResult(false, "No response from server")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error testing connection", e)
            NetworkResult(false, "Connection test failed: ${e.message}")
        }
    }
}