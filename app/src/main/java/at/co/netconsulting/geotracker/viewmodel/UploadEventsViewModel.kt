package at.co.netconsulting.geotracker.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.sync.GeoTrackerApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UploadEventsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = FitnessTrackerDatabase.getInstance(application)
    private val apiClient = GeoTrackerApiClient(application)

    private val _unuploadedEvents = MutableStateFlow<List<Event>>(emptyList())
    val unuploadedEvents: StateFlow<List<Event>> = _unuploadedEvents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uploadProgress = MutableStateFlow<Map<Int, UploadState>>(emptyMap())
    val uploadProgress: StateFlow<Map<Int, UploadState>> = _uploadProgress.asStateFlow()

    private val _selectedEvents = MutableStateFlow<Set<Int>>(emptySet())
    val selectedEvents: StateFlow<Set<Int>> = _selectedEvents.asStateFlow()

    private val _showAllEvents = MutableStateFlow(false)
    val showAllEvents: StateFlow<Boolean> = _showAllEvents.asStateFlow()

    sealed class UploadState {
        object Idle : UploadState()
        object Checking : UploadState()
        object NeedsUpload : UploadState()  // Checked and not on server
        object Uploading : UploadState()
        data class Success(val message: String) : UploadState()
        data class AlreadyExists(val sessionId: String) : UploadState()
        data class Error(val message: String) : UploadState()
    }

    companion object {
        private const val TAG = "UploadEventsViewModel"
    }

    init {
        loadUnuploadedEvents()
    }

    fun loadUnuploadedEvents() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val events = if (_showAllEvents.value) {
                    // Load all events, not just unuploaded
                    database.eventDao().getAllEvents().first()
                } else {
                    // Load events that are not marked as uploaded
                    withContext(Dispatchers.IO) {
                        database.eventDao().getUnuploadedEvents()
                    }
                }
                _unuploadedEvents.value = events
                Log.d(TAG, "Loaded ${events.size} events (showAll=${_showAllEvents.value})")

                // Reset upload progress for new events
                val progressMap = events.associate { it.eventId to UploadState.Idle }
                _uploadProgress.value = progressMap
            } catch (e: Exception) {
                Log.e(TAG, "Error loading events", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleShowAllEvents() {
        _showAllEvents.value = !_showAllEvents.value
        loadUnuploadedEvents()
    }

    fun resetUploadStatus(eventId: Int) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Resetting upload status for event $eventId - setting sessionId to null")
                database.eventDao().updateEventUploadStatus(
                    eventId = eventId,
                    sessionId = null,  // Clear sessionId so a new one will be generated
                    isUploaded = false,
                    uploadedAt = null
                )
                Log.d(TAG, "Reset complete for event $eventId")
                loadUnuploadedEvents()
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting upload status for event $eventId", e)
            }
        }
    }

    fun toggleEventSelection(eventId: Int) {
        val currentSelected = _selectedEvents.value.toMutableSet()
        if (currentSelected.contains(eventId)) {
            currentSelected.remove(eventId)
        } else {
            currentSelected.add(eventId)
        }
        _selectedEvents.value = currentSelected
    }

    fun selectAll() {
        _selectedEvents.value = _unuploadedEvents.value.map { it.eventId }.toSet()
    }

    fun deselectAll() {
        _selectedEvents.value = emptySet()
    }

    /**
     * Check all selected events to see if they already exist on the server
     */
    fun checkSelectedEvents() {
        val selectedEventIds = _selectedEvents.value
        val eventsToCheck = _unuploadedEvents.value.filter { it.eventId in selectedEventIds }

        if (eventsToCheck.isEmpty()) {
            Log.w(TAG, "No events selected for check")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true

                // Get user info once
                val sharedPreferences = getApplication<Application>()
                    .getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                val firstname = sharedPreferences.getString("firstname", "") ?: ""
                val lastname = sharedPreferences.getString("lastname", "")
                val birthdate = sharedPreferences.getString("birthdate", "")

                if (firstname.isBlank()) {
                    Log.e(TAG, "No firstname configured")
                    eventsToCheck.forEach { event ->
                        updateProgress(event.eventId, UploadState.Error("User firstname not configured"))
                    }
                    return@launch
                }

                eventsToCheck.forEach { event ->
                    checkEventStatus(event, firstname, lastname, birthdate)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun checkEventStatus(
        event: Event,
        firstname: String,
        lastname: String?,
        birthdate: String?
    ) {
        Log.d(TAG, "Checking event: ${event.eventName} (ID: ${event.eventId})")
        updateProgress(event.eventId, UploadState.Checking)

        val findResult = withContext(Dispatchers.IO) {
            apiClient.findSessionByDateAndUser(
                eventDate = event.eventDate,
                firstname = firstname,
                lastname = lastname,
                birthdate = birthdate
            )
        }

        findResult.fold(
            onSuccess = { result ->
                if (result.found && result.sessionId != null) {
                    Log.d(TAG, "Session already exists: ${result.sessionId}")

                    // Update local event with the remote sessionId
                    withContext(Dispatchers.IO) {
                        database.eventDao().updateEventUploadStatus(
                            event.eventId,
                            result.sessionId,
                            true,
                            System.currentTimeMillis()
                        )
                    }

                    updateProgress(event.eventId, UploadState.AlreadyExists(result.sessionId))
                } else {
                    Log.d(TAG, "Session doesn't exist on server, needs upload")
                    updateProgress(event.eventId, UploadState.NeedsUpload)
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Error checking session existence: ${error.message}")
                updateProgress(event.eventId, UploadState.Error("Check failed: ${error.message}"))
            }
        )
    }

    /**
     * Upload events that have been checked and need uploading (NeedsUpload state)
     */
    fun uploadSelectedEvents() {
        val selectedEventIds = _selectedEvents.value
        val eventsToUpload = _unuploadedEvents.value.filter { event ->
            event.eventId in selectedEventIds &&
            _uploadProgress.value[event.eventId] == UploadState.NeedsUpload
        }

        if (eventsToUpload.isEmpty()) {
            Log.w(TAG, "No events need uploading (run check first or all already uploaded)")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true

                eventsToUpload.forEach { event ->
                    performUpload(event)
                }

                // Reload the list after all uploads complete
                loadUnuploadedEvents()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadEvent(event: Event) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Processing event: ${event.eventName} (ID: ${event.eventId})")

                // Set checking state
                updateProgress(event.eventId, UploadState.Checking)

                // Get user info
                val sharedPreferences = getApplication<Application>()
                    .getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
                val firstname = sharedPreferences.getString("firstname", "") ?: ""
                val lastname = sharedPreferences.getString("lastname", "")
                val birthdate = sharedPreferences.getString("birthdate", "")

                if (firstname.isBlank()) {
                    Log.e(TAG, "No firstname configured")
                    updateProgress(event.eventId, UploadState.Error("User firstname not configured"))
                    return@launch
                }

                // Check if session already exists on server
                Log.d(TAG, "Checking if session exists for date: ${event.eventDate}, user: $firstname")

                val findResult = withContext(Dispatchers.IO) {
                    apiClient.findSessionByDateAndUser(
                        eventDate = event.eventDate,
                        firstname = firstname,
                        lastname = lastname,
                        birthdate = birthdate
                    )
                }

                findResult.fold(
                    onSuccess = { result ->
                        if (result.found && result.sessionId != null) {
                            // Session already exists on server
                            Log.d(TAG, "Session already exists: ${result.sessionId}")

                            // Update local event with the remote sessionId
                            withContext(Dispatchers.IO) {
                                database.eventDao().updateEventUploadStatus(
                                    event.eventId,
                                    result.sessionId,
                                    true,
                                    System.currentTimeMillis()
                                )
                            }

                            updateProgress(event.eventId, UploadState.AlreadyExists(result.sessionId))
                        } else {
                            // Session doesn't exist, upload it
                            Log.d(TAG, "Session doesn't exist, uploading...")
                            performUpload(event)
                        }
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Error checking session existence: ${error.message}")
                        updateProgress(event.eventId, UploadState.Error("Check failed: ${error.message}"))
                    }
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error processing event ${event.eventId}", e)
                updateProgress(event.eventId, UploadState.Error(e.message ?: "Unknown error"))
            }
        }
    }

    private suspend fun performUpload(event: Event) {
        updateProgress(event.eventId, UploadState.Uploading)

        val result = withContext(Dispatchers.IO) {
            apiClient.createSession(event)
        }

        result.fold(
            onSuccess = { response ->
                Log.d(TAG, "Upload successful: ${event.eventName}")
                updateProgress(event.eventId, UploadState.Success(response.message ?: "Uploaded successfully"))
            },
            onFailure = { error ->
                Log.e(TAG, "Upload failed: ${error.message}")
                updateProgress(event.eventId, UploadState.Error(error.message ?: "Upload failed"))
            }
        )
    }

    private fun updateProgress(eventId: Int, state: UploadState) {
        val currentProgress = _uploadProgress.value.toMutableMap()
        currentProgress[eventId] = state
        _uploadProgress.value = currentProgress
    }

    fun clearUploadState(eventId: Int) {
        updateProgress(eventId, UploadState.Idle)
    }
}
