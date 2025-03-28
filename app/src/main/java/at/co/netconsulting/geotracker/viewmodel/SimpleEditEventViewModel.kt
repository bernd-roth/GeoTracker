package at.co.netconsulting.geotracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.co.netconsulting.geotracker.data.SimpleEventState
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SimpleEditEventViewModel(private val database: FitnessTrackerDatabase) : ViewModel() {

    private val _eventState = MutableStateFlow(SimpleEventState())
    val eventState: StateFlow<SimpleEventState> = _eventState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    // Load existing event data
    fun loadEvent(eventId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // Get the event
                val event = database.eventDao().getEventById(eventId)

                if (event != null) {
                    // Update state with loaded data - only the fields we need
                    _eventState.value = SimpleEventState(
                        eventId = event.eventId,
                        eventName = event.eventName,
                        eventDate = event.eventDate,
                        artOfSport = event.artOfSport,
                        originalEvent = event
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
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

    // Save changes - only update the basic event details
    fun saveEvent() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

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

                    // Save to database
                    database.eventDao().updateEvent(updatedEvent)

                    _saveSuccess.value = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}