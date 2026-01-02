package at.co.netconsulting.geotracker.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.service.SessionUploadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UploadEventsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = FitnessTrackerDatabase.getInstance(application)
    private val uploadService = SessionUploadService(application)

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
        object Uploading : UploadState()
        data class Success(val pointsInserted: Int, val pointsSkipped: Int) : UploadState()
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
                    uploadService.getUnuploadedEvents()
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

    fun uploadSelectedEvents() {
        val selectedEventIds = _selectedEvents.value
        val eventsToUpload = _unuploadedEvents.value.filter { it.eventId in selectedEventIds }

        if (eventsToUpload.isEmpty()) {
            Log.w(TAG, "No events selected for upload")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true

                eventsToUpload.forEach { event ->
                    uploadEvent(event)
                }

                // Reload the list after all uploads complete
                loadUnuploadedEvents()
                deselectAll()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadEvent(event: Event) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Uploading event: ${event.eventName} (ID: ${event.eventId})")

                // Set uploading state
                val currentProgress = _uploadProgress.value.toMutableMap()
                currentProgress[event.eventId] = UploadState.Uploading
                _uploadProgress.value = currentProgress

                // Perform upload
                val result = uploadService.uploadEvent(event)

                // Update state based on result
                currentProgress[event.eventId] = if (result.success) {
                    Log.d(TAG, "Upload successful: ${event.eventName}")
                    UploadState.Success(result.pointsInserted, result.pointsSkipped)
                } else {
                    Log.e(TAG, "Upload failed: ${result.message}")
                    UploadState.Error(result.message)
                }
                _uploadProgress.value = currentProgress

            } catch (e: Exception) {
                Log.e(TAG, "Error uploading event ${event.eventId}", e)
                val currentProgress = _uploadProgress.value.toMutableMap()
                currentProgress[event.eventId] = UploadState.Error(e.message ?: "Unknown error")
                _uploadProgress.value = currentProgress
            }
        }
    }

    fun clearUploadState(eventId: Int) {
        val currentProgress = _uploadProgress.value.toMutableMap()
        currentProgress[eventId] = UploadState.Idle
        _uploadProgress.value = currentProgress
    }
}
