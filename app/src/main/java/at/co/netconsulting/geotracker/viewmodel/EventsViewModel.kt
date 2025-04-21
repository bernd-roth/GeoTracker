package at.co.netconsulting.geotracker.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.co.netconsulting.geotracker.data.EventWithDetails
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.Locale

class EventsViewModel(private val database: FitnessTrackerDatabase) : ViewModel() {

    private val _eventsWithDetails = MutableStateFlow<List<EventWithDetails>>(emptyList())
    val eventsWithDetails: StateFlow<List<EventWithDetails>> = _eventsWithDetails.asStateFlow()

    private val _filteredEventsWithDetails = MutableStateFlow<List<EventWithDetails>>(emptyList())
    val filteredEventsWithDetails: StateFlow<List<EventWithDetails>> = _filteredEventsWithDetails.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var currentPage = 0
    private val pageSize = 20
    private var hasMoreEvents = true

    // Date range filter
    private var startDateFilter: String? = null
    private var endDateFilter: String? = null

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        currentPage = 0
        viewModelScope.launch {
            updateFilteredEvents()
        }
    }

    fun filterByDateRange(startDate: String? = null, endDate: String? = null) {
        startDateFilter = startDate
        endDateFilter = endDate
        currentPage = 0
        viewModelScope.launch {
            updateFilteredEvents()
        }
    }

    private suspend fun updateFilteredEvents() {
        val query = _searchQuery.value.lowercase()
        val events = _eventsWithDetails.value

        val filtered = events.filter { event ->
            val matchesSearch = query.isEmpty() ||
                    event.event.eventName.lowercase().contains(query) ||
                    event.event.artOfSport.lowercase().contains(query) ||
                    event.event.comment.lowercase().contains(query)

            val matchesDateRange = if (startDateFilter != null && endDateFilter != null) {
                isDateInRange(event.event.eventDate, startDateFilter!!, endDateFilter!!)
            } else {
                true
            }

            matchesSearch && matchesDateRange
        }

        _filteredEventsWithDetails.value = filtered
    }

    private fun isDateInRange(dateStr: String, startDateStr: String, endDateStr: String): Boolean {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr)
            val startDate = sdf.parse(startDateStr)
            val endDate = sdf.parse(endDateStr)

            if (date != null && startDate != null && endDate != null) {
                return !date.before(startDate) && !date.after(endDate)
            }
        } catch (e: Exception) {
            Log.e("EventsViewModel", "Error parsing dates: ${e.message}")
        }
        return false
    }

    fun loadEvents() {
        if (_isLoading.value) return

        currentPage = 0
        hasMoreEvents = true
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val events = loadEventsWithDetails(0, pageSize)
                _eventsWithDetails.value = events
                updateFilteredEvents()
            } catch (e: Exception) {
                Log.e("EventsViewModel", "Error loading events: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMoreEvents() {
        if (_isLoading.value || !hasMoreEvents) return

        _isLoading.value = true
        currentPage++

        viewModelScope.launch {
            try {
                val newEvents = loadEventsWithDetails(currentPage * pageSize, pageSize)
                if (newEvents.isEmpty()) {
                    hasMoreEvents = false
                } else {
                    _eventsWithDetails.value = _eventsWithDetails.value + newEvents
                    updateFilteredEvents()
                }
            } catch (e: Exception) {
                Log.e("EventsViewModel", "Error loading more events: ${e.message}")
                currentPage-- // Revert page increment on failure
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadEventsWithDetails(offset: Int, limit: Int): List<EventWithDetails> {
        return withContext(Dispatchers.IO) {
            try {
                // Fixed: use the correct method name getEventsPaged instead of getEventsWithLimit
                val events = database.eventDao().getEventsPaged(limit, offset)

                processEvents(events)
            } catch (e: Exception) {
                Log.e("EventsViewModel", "Error in loadEventsWithDetails: ${e.message}")
                emptyList()
            }
        }
    }

    private suspend fun processEvents(events: List<Event>): List<EventWithDetails> {
        return events.map { event ->
            val eventId = event.eventId

            // Calculate heart rate statistics
            val metrics = database.metricDao().getMetricsForEvent(eventId)
            val heartRates = metrics.filter { it.heartRate > 0 }.map { it.heartRate }
            val minHeartRate = heartRates.minOrNull() ?: 0
            val maxHeartRate = heartRates.maxOrNull() ?: 0
            val avgHeartRate = if (heartRates.isNotEmpty())
                heartRates.sum() / heartRates.size
            else 0

            // Get heart rate device name
            val heartRateDevice = metrics.firstOrNull {
                it.heartRate > 0 && it.heartRateDevice.isNotEmpty() && it.heartRateDevice != "None"
            }?.heartRateDevice ?: ""

            // Calculate basic metrics
            val totalDistance = metrics.maxByOrNull { it.distance }?.distance ?: 0.0
            val avgSpeed = if (metrics.isNotEmpty()) metrics.sumOf { it.speed.toDouble() } / metrics.size else 0.0

            // Get elevation data
            val elevations = metrics.map { it.elevation.toDouble() }
            val maxElevation = elevations.maxOrNull() ?: 0.0
            val minElevation = if (elevations.isNotEmpty()) elevations.minOrNull() ?: 0.0 else 0.0
            val maxElevationGain = metrics.maxByOrNull { it.elevationGain }?.elevationGain?.toDouble() ?: 0.0

            // Get time range
            val timeRange = database.metricDao().getEventTimeRange(eventId)
            val startTime = timeRange?.minTime ?: 0
            val endTime = timeRange?.maxTime ?: 0

            // Fixed: use getLatestWeatherForEvent from your DAO
            val weather = database.eventDao().getLatestWeatherForEvent(eventId)

            // Get location data for map
            val locations = database.locationDao().getLocationsForEvent(eventId)
            val geoPoints = locations.map { GeoPoint(it.latitude, it.longitude) }

            // Get lap times (if available)
            val lapTimes = try {
                database.lapTimeDao().getLapTimesForEvent(eventId).map { it.endTime - it.startTime }
            } catch (e: Exception) {
                emptyList<Long>()
            }

            // Fixed: use getLastDeviceStatusByEvent
            val deviceStatus = database.deviceStatusDao().getLastDeviceStatusByEvent(eventId)
            val satellites = deviceStatus?.numberOfSatellites?.toIntOrNull() ?: 0

            EventWithDetails(
                event = event,
                totalDistance = totalDistance,
                averageSpeed = avgSpeed,
                maxElevation = maxElevation,
                minElevation = minElevation,
                maxElevationGain = maxElevationGain,
                startTime = startTime,
                endTime = endTime,
                weather = weather,
                laps = lapTimes,
                locationPoints = geoPoints,
                satellites = satellites,
                minHeartRate = minHeartRate,
                maxHeartRate = maxHeartRate,
                avgHeartRate = avgHeartRate,
                heartRateDevice = heartRateDevice
            )
        }
    }

    suspend fun deleteEvent(eventId: Int) {
        withContext(Dispatchers.IO) {
            try {
                // Get the event first, then delete it
                val event = database.eventDao().getEventById(eventId)
                if (event != null) {
                    database.eventDao().deleteEvent(event)

                    // Refresh events after deletion
                    val events = loadEventsWithDetails(0, (currentPage + 1) * pageSize)
                    _eventsWithDetails.value = events
                    updateFilteredEvents()
                } else {

                }
            } catch (e: Exception) {
                Log.e("EventsViewModel", "Error deleting event: ${e.message}")
            }
        }
    }
}