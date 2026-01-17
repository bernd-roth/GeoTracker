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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * REST API client for PlannedEvents
 * Handles upload/download of planned events via Flask REST API
 */
class PlannedEventsNetworkManager(private val context: Context) {
    private val database = FitnessTrackerDatabase.getInstance(context)
    private val reminderManager = ReminderManager(context)

    companion object {
        private const val TAG = "PlannedEventsNetwork"
        private const val TIMEOUT_SECONDS = 30L
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
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

    private fun getApiBaseUrl(): String? {
        val sharedPreferences = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        val serverAddress = sharedPreferences.getString("websocketserver", "") ?: ""
        return if (serverAddress.isNotBlank()) {
            "https://$serverAddress/api"
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
     * Upload planned events to the server via REST API
     */
    suspend fun uploadPlannedEvents(): NetworkResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting upload of planned events via REST API")

            val baseUrl = getApiBaseUrl()
            if (baseUrl == null) {
                Log.e(TAG, "Server URL not configured")
                return@withContext NetworkResult(false, "Server URL not configured")
            }

            if (!isNetworkAvailable()) {
                Log.e(TAG, "No network connectivity available")
                return@withContext NetworkResult(false, "No network connectivity")
            }

            val (firstname, lastname, birthdate) = getUserInfo()
            if (firstname.isBlank()) {
                Log.e(TAG, "User information not configured for upload")
                return@withContext NetworkResult(false, "User information not configured")
            }

            val currentUserId = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                .getInt("current_user_id", 1)

            val plannedEvents = database.plannedEventDao().getAllPlannedEventsForUser(currentUserId)
            Log.d(TAG, "Found ${plannedEvents.size} planned events to upload for user $currentUserId")

            if (plannedEvents.isEmpty()) {
                return@withContext NetworkResult(false, "No planned events to upload")
            }

            // Build JSON request
            val eventsArray = JSONArray()
            plannedEvents.forEach { event ->
                val eventJson = JSONObject().apply {
                    put("plannedEventName", event.plannedEventName)
                    put("plannedEventDate", event.plannedEventDate)
                    put("plannedEventType", event.plannedEventType)
                    put("plannedEventCountry", event.plannedEventCountry)
                    put("plannedEventCity", event.plannedEventCity)
                    event.plannedLatitude?.let { put("plannedLatitude", it) }
                    event.plannedLongitude?.let { put("plannedLongitude", it) }
                    put("isEnteredAndFinished", event.isEnteredAndFinished)
                    put("website", event.website)
                    put("comment", event.comment)
                    put("reminderDateTime", event.reminderDateTime)
                    put("isReminderActive", event.isReminderActive)
                    put("isRecurring", event.isRecurring)
                    put("recurringType", event.recurringType)
                    put("recurringInterval", event.recurringInterval)
                    put("recurringEndDate", event.recurringEndDate)
                    put("recurringDaysOfWeek", event.recurringDaysOfWeek)
                }
                eventsArray.put(eventJson)
            }

            val requestJson = JSONObject().apply {
                put("events", eventsArray)
                put("firstname", firstname)
                put("lastname", lastname)
                put("birthdate", birthdate)
            }

            val requestBody = requestJson.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/planned-events/upload")
                .post(requestBody)
                .build()

            Log.d(TAG, "Uploading ${eventsArray.length()} events to $baseUrl/planned-events/upload")

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val data = json.optJSONObject("data")

                val errors = mutableListOf<String>()
                data?.optJSONArray("errors")?.let { errorsArray ->
                    for (i in 0 until errorsArray.length()) {
                        errors.add(errorsArray.getString(i))
                    }
                }

                val result = NetworkResult(
                    success = true,
                    message = "Events uploaded successfully",
                    uploadedCount = data?.optInt("uploaded_count", 0) ?: 0,
                    duplicateCount = data?.optInt("duplicate_count", 0) ?: 0,
                    errorCount = data?.optInt("error_count", 0) ?: 0,
                    errors = errors
                )

                Log.d(TAG, "Upload successful: ${result.uploadedCount} uploaded, ${result.duplicateCount} duplicates, ${result.errorCount} errors")
                return@withContext result
            } else {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Upload failed: ${response.code} - $errorBody")
                val errorJson = try { JSONObject(errorBody) } catch (e: Exception) { JSONObject() }
                val errorMessage = errorJson.optString("error", "Upload failed: ${response.code}")
                return@withContext NetworkResult(false, errorMessage)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error uploading planned events", e)
            return@withContext NetworkResult(false, "Upload failed: ${e.message}")
        }
    }

    /**
     * Download planned events from the server via REST API
     */
    suspend fun downloadPlannedEvents(): NetworkResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting download of planned events via REST API")

            val baseUrl = getApiBaseUrl()
            if (baseUrl == null) {
                Log.e(TAG, "Server URL not configured")
                return@withContext NetworkResult(false, "Server URL not configured")
            }

            if (!isNetworkAvailable()) {
                Log.e(TAG, "No network connectivity available")
                return@withContext NetworkResult(false, "No network connectivity")
            }

            val (firstname, lastname, birthdate) = getUserInfo()
            if (firstname.isBlank()) {
                Log.e(TAG, "User information not configured for download")
                return@withContext NetworkResult(false, "User information not configured")
            }

            val currentUserId = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                .getInt("current_user_id", 1)

            // Build URL with query parameters
            val urlBuilder = StringBuilder("$baseUrl/planned-events/download?firstname=$firstname")
            if (lastname.isNotBlank()) urlBuilder.append("&lastname=$lastname")
            if (birthdate.isNotBlank()) urlBuilder.append("&birthdate=$birthdate")
            urlBuilder.append("&exclude_user_events=true")

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .get()
                .build()

            Log.d(TAG, "Downloading events from $urlBuilder")

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val data = json.optJSONObject("data")
                val eventsArray = data?.optJSONArray("events")

                var savedCount = 0
                var duplicateCount = 0
                val errors = mutableListOf<String>()

                Log.d(TAG, "Processing ${eventsArray?.length() ?: 0} downloaded events")

                eventsArray?.let { events ->
                    for (i in 0 until events.length()) {
                        try {
                            val eventJson = events.getJSONObject(i)

                            val eventName = eventJson.optString("planned_event_name", "")
                            val eventDate = eventJson.optString("planned_event_date", "")
                            val eventCountry = eventJson.optString("planned_event_country", "")
                            val eventCity = eventJson.optString("planned_event_city", "")

                            // Check for duplicates locally
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
                                continue
                            }

                            // Create new PlannedEvent
                            val plannedEvent = PlannedEvent(
                                userId = currentUserId,
                                plannedEventName = eventName,
                                plannedEventDate = eventDate,
                                plannedEventType = eventJson.optString("planned_event_type", ""),
                                plannedEventCountry = eventCountry,
                                plannedEventCity = eventCity,
                                plannedLatitude = if (eventJson.isNull("planned_latitude")) null else eventJson.optDouble("planned_latitude"),
                                plannedLongitude = if (eventJson.isNull("planned_longitude")) null else eventJson.optDouble("planned_longitude"),
                                isEnteredAndFinished = eventJson.optBoolean("is_entered_and_finished", false),
                                website = eventJson.optString("website", ""),
                                comment = eventJson.optString("comment", ""),
                                reminderDateTime = eventJson.optString("reminder_date_time", ""),
                                isReminderActive = eventJson.optBoolean("is_reminder_active", false),
                                isRecurring = eventJson.optBoolean("is_recurring", false),
                                recurringType = eventJson.optString("recurring_type", ""),
                                recurringInterval = eventJson.optInt("recurring_interval", 1),
                                recurringEndDate = eventJson.optString("recurring_end_date", ""),
                                recurringDaysOfWeek = eventJson.optString("recurring_days_of_week", "")
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
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "Download failed: ${response.code} - $errorBody")
                val errorJson = try { JSONObject(errorBody) } catch (e: Exception) { JSONObject() }
                val errorMessage = errorJson.optString("error", "Download failed: ${response.code}")
                return@withContext NetworkResult(false, errorMessage)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error downloading planned events", e)
            return@withContext NetworkResult(false, "Download failed: ${e.message}")
        }
    }

    /**
     * Test the REST API connection
     */
    suspend fun testPlannedEventsConnection(): NetworkResult = withContext(Dispatchers.IO) {
        val baseUrl = getApiBaseUrl()
        if (baseUrl == null) {
            Log.e(TAG, "Server URL not configured")
            return@withContext NetworkResult(false, "Server URL not configured")
        }

        Log.d(TAG, "Testing REST API connection to: $baseUrl/health")

        return@withContext try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val status = json.optString("status", "unknown")
                Log.d(TAG, "Connection test successful: $status")
                NetworkResult(true, "Connection successful: $status")
            } else {
                Log.e(TAG, "Connection test failed: ${response.code}")
                NetworkResult(false, "Connection failed: ${response.code}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error testing connection", e)
            NetworkResult(false, "Connection test failed: ${e.message}")
        }
    }
}
