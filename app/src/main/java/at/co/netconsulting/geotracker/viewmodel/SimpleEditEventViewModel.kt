package at.co.netconsulting.geotracker.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.co.netconsulting.geotracker.data.SimpleEventState
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.sync.GeoTrackerApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SimpleEditEventViewModel(
    private val database: FitnessTrackerDatabase,
    private val context: Context
) : ViewModel() {

    companion object {
        private const val TAG = "SimpleEditEventVM"
    }

    private val apiClient = GeoTrackerApiClient(context)

    private val _eventState = MutableStateFlow(SimpleEventState())
    val eventState: StateFlow<SimpleEventState> = _eventState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Syncing : SyncStatus()
        data class Success(val message: String) : SyncStatus()
        data class Error(val message: String) : SyncStatus()
    }

    // Load existing event data
    fun loadEvent(eventId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val event = database.eventDao().getEventById(eventId)

                if (event != null) {
                    _eventState.value = SimpleEventState(
                        eventId = event.eventId,
                        eventName = event.eventName,
                        eventDate = event.eventDate,
                        artOfSport = event.artOfSport,
                        originalEvent = event
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading event", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Update a field in the event state
    fun updateEventField(field: String, value: String) {
        val currentState = _eventState.value

        _eventState.value = when (field) {
            "name" -> currentState.copy(eventName = value)
            "date" -> currentState.copy(eventDate = value)
            "sport" -> currentState.copy(artOfSport = value)
            else -> currentState
        }
    }

    // Save changes locally and sync with remote server via REST API
    fun saveEvent() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _syncStatus.value = SyncStatus.Idle

            try {
                val currentState = _eventState.value
                val originalEvent = currentState.originalEvent

                if (originalEvent != null) {
                    // Update only the basic fields in the event
                    val updatedEvent = originalEvent.copy(
                        eventName = currentState.eventName,
                        eventDate = currentState.eventDate,
                        artOfSport = currentState.artOfSport
                    )

                    // Save to local database first
                    database.eventDao().updateEvent(updatedEvent)
                    Log.d(TAG, "Event saved locally: ${updatedEvent.eventId}")

                    // Now sync with remote server via REST API
                    syncWithRemote(updatedEvent)

                    _saveSuccess.value = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving event", e)
                _syncStatus.value = SyncStatus.Error("Failed to save: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun syncWithRemote(event: Event) {
        _syncStatus.value = SyncStatus.Syncing

        try {
            // Get user info for finding existing session
            val sharedPreferences = context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
            val firstname = sharedPreferences.getString("firstname", "") ?: ""
            val lastname = sharedPreferences.getString("lastname", "")
            val birthdate = sharedPreferences.getString("birthdate", "")

            if (firstname.isBlank()) {
                Log.e(TAG, "No firstname configured, cannot sync")
                _syncStatus.value = SyncStatus.Error("User firstname not configured")
                return
            }

            // First, try to find existing session by date and user (more reliable than sessionId)
            Log.d(TAG, "Finding session by date: ${event.eventDate}, user: $firstname")

            val findResult = apiClient.findSessionByDateAndUser(
                eventDate = event.eventDate,
                firstname = firstname,
                lastname = lastname,
                birthdate = birthdate
            )

            findResult.fold(
                onSuccess = { result ->
                    if (result.found && result.sessionId != null) {
                        // Session found on remote, update it
                        Log.d(TAG, "Found existing remote session: ${result.sessionId}, updating via PUT")
                        updateRemoteSession(event, result.sessionId)

                        // Also update local event with the correct sessionId if different
                        if (event.sessionId != result.sessionId) {
                            Log.d(TAG, "Updating local sessionId from ${event.sessionId} to ${result.sessionId}")
                            database.eventDao().updateEventUploadStatus(
                                event.eventId,
                                result.sessionId,
                                true,
                                System.currentTimeMillis()
                            )
                        }
                    } else {
                        // Session not found, create new one
                        Log.d(TAG, "No existing session found for date ${event.eventDate}, creating new via POST")
                        createRemoteSession(event)
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to find session: ${error.message}")
                    _syncStatus.value = SyncStatus.Error("Sync failed: ${error.message}")
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing with remote", e)
            _syncStatus.value = SyncStatus.Error("Sync error: ${e.message}")
        }
    }

    private suspend fun updateRemoteSession(event: Event, sessionId: String) {
        val result = apiClient.updateSession(
            sessionId = sessionId,
            eventName = event.eventName,
            sportType = event.artOfSport,
            comment = event.comment
        )

        result.fold(
            onSuccess = { response ->
                Log.d(TAG, "Remote session updated successfully: ${response.message}")
                _syncStatus.value = SyncStatus.Success("Saved and synced with server")
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to update remote session: ${error.message}")
                _syncStatus.value = SyncStatus.Error("Local saved, sync failed: ${error.message}")
            }
        )
    }

    private suspend fun createRemoteSession(event: Event) {
        // Re-fetch event from database to ensure we have latest data
        val freshEvent = database.eventDao().getEventById(event.eventId)

        if (freshEvent == null) {
            _syncStatus.value = SyncStatus.Error("Event not found in database")
            return
        }

        val result = apiClient.createSession(freshEvent)

        result.fold(
            onSuccess = { response ->
                Log.d(TAG, "Session created successfully: ${response.message}")
                _syncStatus.value = SyncStatus.Success("Saved and uploaded to server")
            },
            onFailure = { error ->
                Log.e(TAG, "Failed to create session: ${error.message}")
                _syncStatus.value = SyncStatus.Error("Local saved, upload failed: ${error.message}")
            }
        )
    }

    fun resetSyncStatus() {
        _syncStatus.value = SyncStatus.Idle
    }
}
