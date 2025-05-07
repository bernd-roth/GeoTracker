package at.co.netconsulting.geotracker.service

import android.content.Context
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import at.co.netconsulting.geotracker.domain.CurrentRecording
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * SessionRecoveryManager handles crash recovery for tracking sessions.
 * It centralizes the logic for saving and restoring session state.
 */
class SessionRecoveryManager(private val context: Context) {

    private val recoveryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val database = FitnessTrackerDatabase.getInstance(context)

    companion object {
        private const val TAG = "SessionRecoveryManager"

        // For singleton implementation
        @Volatile
        private var INSTANCE: SessionRecoveryManager? = null

        fun getInstance(context: Context): SessionRecoveryManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SessionRecoveryManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        // Shared preferences constants
        const val PREF_SERVICE_STATE = "ServiceState"
        const val PREF_WAS_RUNNING = "was_running"
        const val PREF_EVENT_ID = "event_id"
        const val PREF_SESSION_ID = "session_id"
        const val PREF_LAST_LATITUDE = "last_latitude"
        const val PREF_LAST_LONGITUDE = "last_longitude"
        const val PREF_COVERED_DISTANCE = "covered_distance"
        const val PREF_LAST_TIMESTAMP = "last_timestamp"
        const val PREF_LAP = "lap"
        const val PREF_LAP_COUNTER = "lap_counter"
        const val PREF_SERVICE_START_TIME = "service_start_time"
        const val PREF_HEART_RATE_DEVICE_ADDRESS = "heart_rate_device_address"
        const val PREF_HEART_RATE_DEVICE_NAME = "heart_rate_device_name"
        const val PREF_IS_PAUSED = "is_paused"
        const val PREF_PAUSE_START_TIME = "pause_start_time"
        const val PREF_LAST_LAP_COMPLETION_TIME = "last_lap_completion_time"
        const val PREF_LAP_START_TIME = "lap_start_time"
    }

    /**
     * Checks if there's a recoverable session
     */
    fun checkForRecoverableSession(): Boolean {
        val prefs = context.getSharedPreferences(PREF_SERVICE_STATE, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_WAS_RUNNING, false)
    }

    /**
     * Returns the eventId of a recoverable session, or -1 if none exists
     */
    fun getRecoverableEventId(): Int {
        val prefs = context.getSharedPreferences(PREF_SERVICE_STATE, Context.MODE_PRIVATE)
        return if (prefs.getBoolean(PREF_WAS_RUNNING, false)) {
            prefs.getInt(PREF_EVENT_ID, -1)
        } else {
            -1
        }
    }

    /**
     * Returns the sessionId of a recoverable session, or empty string if none exists
     */
    fun getRecoverableSessionId(): String {
        val prefs = context.getSharedPreferences(PREF_SERVICE_STATE, Context.MODE_PRIVATE)
        return if (prefs.getBoolean(PREF_WAS_RUNNING, false)) {
            prefs.getString(PREF_SESSION_ID, "") ?: ""
        } else {
            ""
        }
    }

    /**
     * Cleans up the recovery state after successful restoration
     */
    fun clearRecoveryState() {
        context.getSharedPreferences(PREF_SERVICE_STATE, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_WAS_RUNNING, false)
            .apply()

        Log.d(TAG, "Recovery state cleared")
    }

    /**
     * Verifies session data consistency between SharedPreferences and database
     * Returns true if session is valid and can be restored
     */
    suspend fun verifySessionConsistency(sessionId: String, eventId: Int): Boolean {
        if (sessionId.isEmpty() || eventId <= 0) {
            Log.e(TAG, "Invalid session parameters: sessionId=$sessionId, eventId=$eventId")
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                // Check if we have recovery data in the database
                val recordCount = database.currentRecordingDao().getRecordCountForSession(sessionId)

                if (recordCount > 0) {
                    Log.d(TAG, "Found $recordCount records for session $sessionId in database")
                    true
                } else {
                    Log.e(TAG, "No records found for session $sessionId in database")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error verifying session consistency", e)
                false
            }
        }
    }

    /**
     * Gets the latest state for a session from the database
     */
    suspend fun getLatestSessionState(sessionId: String): CurrentRecording? {
        return withContext(Dispatchers.IO) {
            try {
                val record = database.currentRecordingDao().getLatestRecordForSession(sessionId)
                Log.d(TAG, "Retrieved latest state for session $sessionId: $record")
                record
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving latest session state", e)
                null
            }
        }
    }

    /**
     * Creates a restoration intent with all the necessary extras from SharedPreferences
     */
    fun buildRestorationIntent(context: Context): android.content.Intent {
        val prefs = context.getSharedPreferences(PREF_SERVICE_STATE, Context.MODE_PRIVATE)

        // Get event details from preferences
        val eventName = prefs.getString("eventName", "Recovered Event") ?: "Recovered Event"
        val eventDate = prefs.getString("eventDate", "") ?: ""
        val artOfSport = prefs.getString("artOfSport", "Running") ?: "Running"
        val comment = prefs.getString("comment", "Automatically recovered after crash")
            ?: "Automatically recovered after crash"
        val clothing = prefs.getString("clothing", "") ?: ""

        // Get heart rate sensor information
        val heartRateDeviceAddress = prefs.getString(PREF_HEART_RATE_DEVICE_ADDRESS, null)
        val heartRateDeviceName = prefs.getString(PREF_HEART_RATE_DEVICE_NAME, null)

        // Create intent with all necessary extras
        val restorationIntent = android.content.Intent(context, ForegroundService::class.java).apply {
            putExtra("eventName", eventName)
            putExtra("eventDate", eventDate)
            putExtra("artOfSport", artOfSport)
            putExtra("comment", comment)
            putExtra("clothing", clothing)
            putExtra("is_restored_session", true)

            // Add heart rate device information if available
            heartRateDeviceAddress?.let { putExtra("heartRateDeviceAddress", it) }
            heartRateDeviceName?.let { putExtra("heartRateDeviceName", it) }
        }

        Log.d(TAG, "Built restoration intent with extras: eventName=$eventName, " +
                "eventDate=$eventDate, artOfSport=$artOfSport, heartRateDevice=$heartRateDeviceName")

        return restorationIntent
    }

    /**
     * Synchronizes the service state with database state to ensure consistency
     */
    fun synchronizeServiceState(sessionId: String) {
        recoveryScope.launch {
            try {
                val latestState = getLatestSessionState(sessionId)

                latestState?.let { state ->
                    // Get current values from shared preferences
                    val prefs = context.getSharedPreferences(PREF_SERVICE_STATE, Context.MODE_PRIVATE)
                    val editor = prefs.edit()

                    // Update with the latest database values
                    editor.putFloat(PREF_LAST_LATITUDE, state.latitude.toFloat())
                    editor.putFloat(PREF_LAST_LONGITUDE, state.longitude.toFloat())
                    editor.putFloat(PREF_COVERED_DISTANCE, state.distance.toFloat())
                    editor.putInt(PREF_LAP, state.lap)
                    editor.putFloat(PREF_LAP_COUNTER, state.currentLapDistance.toFloat())

                    // Convert epoch seconds to millis for startDateTime
                    val startDateTimeEpochMillis = state.startDateTimeEpoch * 1000
                    editor.putLong(PREF_SERVICE_START_TIME, state.startDateTimeEpoch)

                    // Also update start time in a more accessible format
                    val startDateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(state.startDateTimeEpoch),
                        ZoneId.systemDefault()
                    )

                    editor.apply()

                    Log.d(TAG, "Synchronized service state with database: " +
                            "distance=${state.distance}, lap=${state.lap}, " +
                            "startTime=$startDateTime")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error synchronizing service state", e)
            }
        }
    }

    /**
     * Handles application crash detection and recovery preparation
     */
    fun setupCrashRecovery() {
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            // This is just a placeholder for potential future enhancements
            // The ForegroundService and onTaskRemoved already handle most crash recovery
            Log.d(TAG, "Crash recovery monitoring initialized")
        }
    }
}