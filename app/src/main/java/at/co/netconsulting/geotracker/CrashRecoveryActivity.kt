package at.co.netconsulting.geotracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.service.ForegroundService
import at.co.netconsulting.geotracker.service.SessionRecoveryManager
import at.co.netconsulting.geotracker.ui.theme.GeoTrackerTheme
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CrashRecoveryActivity : ComponentActivity() {
    private lateinit var recoveryManager: SessionRecoveryManager
    private lateinit var database: FitnessTrackerDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize database and recovery manager
        database = FitnessTrackerDatabase.getInstance(this)
        recoveryManager = SessionRecoveryManager.getInstance(this)

        // Set Compose content - just a simple loading screen
        setContent {
            GeoTrackerTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }

        // Check for crashed session in background
        lifecycleScope.launch {
            checkForCrashedSession()
        }
    }

    private suspend fun checkForCrashedSession() {
        // Check if we have a recoverable session
        if (recoveryManager.checkForRecoverableSession()) {
            val eventId = recoveryManager.getRecoverableEventId()
            val sessionId = recoveryManager.getRecoverableSessionId()

            Log.d(TAG, "Found potentially recoverable session - eventId=$eventId, sessionId=$sessionId")

            // Verify session consistency
            if (recoveryManager.verifySessionConsistency(sessionId, eventId)) {
                // We have a valid crashed session to recover
                showRecoveryDialog(eventId, sessionId)
            } else {
                Log.d(TAG, "Session verification failed - no database records found")
                proceedToMainActivity()
            }
        } else {
            // No crashed session, proceed normally
            Log.d(TAG, "No crashed session detected")
            proceedToMainActivity()
        }
    }

    private fun showRecoveryDialog(eventId: Int, sessionId: String) {
        lifecycleScope.launch {
            try {
                // Get event details
                val event = database.eventDao().getEventById(eventId)

                // Get last state timestamp
                val lastState = recoveryManager.getLatestSessionState(sessionId)
                val lastUpdateTime = lastState?.timestamp ?: 0L

                // Format timestamp for display
                val formattedTime = if (lastUpdateTime > 0) {
                    val dateTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(lastUpdateTime),
                        ZoneId.systemDefault()
                    )
                    dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                } else {
                    "unknown time"
                }

                // Calculate total distance
                val totalDistance = lastState?.distance ?: 0.0
                val distanceInKm = totalDistance / 1000.0

                // Use traditional AlertDialog for simplicity
                runOnUiThread {
                    android.app.AlertDialog.Builder(this@CrashRecoveryActivity)
                        .setTitle("Recover Tracking Session?")
                        .setMessage(
                            "A tracking session was interrupted unexpectedly.\n\n" +
                                    "Event: ${event?.eventName ?: "Unknown"}\n" +
                                    "Sport: ${event?.artOfSport ?: "Unknown"}\n" +
                                    "Distance: ${String.format("%.2f", distanceInKm)} km\n" +
                                    "Last updated: $formattedTime\n\n" +
                                    "Would you like to resume this tracking session?"
                        )
                        .setPositiveButton("Resume") { _, _ ->
                            restoreSession()
                        }
                        .setNegativeButton("Discard") { _, _ ->
                            recoveryManager.clearRecoveryState()
                            proceedToMainActivity()
                        }
                        .setCancelable(false)
                        .show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error showing recovery dialog", e)
                proceedToMainActivity()
            }
        }
    }

    private fun restoreSession() {
        try {
            Log.d(TAG, "Restoring crashed session")

            // Build and start the restoration intent
            val restorationIntent = recoveryManager.buildRestorationIntent(this)

            // Use startForegroundService for Android 8+
            startForegroundService(restorationIntent)

            // Update recording state
            getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("is_recording", true)
                .apply()

            // Clean up recovery state (will be re-saved by service)
            recoveryManager.clearRecoveryState()

            // Proceed to main activity
            proceedToMainActivity()
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring session", e)
            proceedToMainActivity()
        }
    }

    private fun proceedToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    companion object {
        private const val TAG = "CrashRecoveryActivity"
    }
}