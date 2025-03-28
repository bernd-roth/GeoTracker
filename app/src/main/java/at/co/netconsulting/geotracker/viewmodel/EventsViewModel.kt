package at.co.netconsulting.geotracker.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.co.netconsulting.geotracker.data.EventWithDetails
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Weather
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class EventsViewModel(private val database: FitnessTrackerDatabase) : ViewModel() {
    private val _eventsWithDetails = MutableStateFlow<List<EventWithDetails>>(emptyList())
    val eventsWithDetails: StateFlow<List<EventWithDetails>> = _eventsWithDetails.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // For managing event filtering
    private val _filteredEventsWithDetails = MutableStateFlow<List<EventWithDetails>>(emptyList())
    val filteredEventsWithDetails: StateFlow<List<EventWithDetails>> = _filteredEventsWithDetails.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var hasMoreEvents = true
    private var currentPage = 0
    private val pageSize = 10

    // Store the full list of loaded events for search operations
    private var allLoadedEvents = mutableListOf<EventWithDetails>()

    fun loadEvents() {
        if (_isLoading.value || !hasMoreEvents) return

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // Collect events from the Flow
                val allEvents = database.eventDao().getAllEvents().first()

                if (allEvents.isEmpty()) {
                    hasMoreEvents = false
                    _isLoading.value = false
                    return@launch
                }

                // Implement pagination from the collected list
                val startIndex = currentPage * pageSize
                if (startIndex >= allEvents.size) {
                    hasMoreEvents = false
                    _isLoading.value = false
                    return@launch
                }

                val endIndex = minOf(startIndex + pageSize, allEvents.size)
                val eventsForPage = allEvents.subList(startIndex, endIndex)

                // Process this page of events
                val eventDetailsList = eventsForPage.map { event ->
                    processEventDetails(event)
                }

                // Store all loaded events for filtering
                allLoadedEvents.addAll(eventDetailsList)

                // Update the state with new events
                _eventsWithDetails.value = allLoadedEvents.toList()

                // Apply current search filter if any
                applySearchFilter(_searchQuery.value)

                // Increment the page for next time
                currentPage++

                // Check if we have more events
                hasMoreEvents = endIndex < allEvents.size

            } catch (e: Exception) {
                // Handle any errors
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun processEventDetails(event: Event): EventWithDetails {
        val eventId = event.eventId

        // Get location points
        val locations = database.locationDao().getLocationsByEventId(eventId)
        val locationPoints = locations.map { location ->
            GeoPoint(location.latitude, location.longitude)
        }

        // Get metrics for calculating lap times and averages
        val metrics = database.metricDao().getMetricsByEventId(eventId)

        // Calculate lap times (assuming 1km per lap)
        val lapTimes = calculateLapTimes(metrics.map { it.distance to it.timeInMilliseconds })

        // Calculate average speed
        val totalDistance = if (metrics.isNotEmpty()) metrics.last().distance else 0.0
        val averageSpeed = if (metrics.isNotEmpty() && totalDistance > 0) {
            val elapsedTimeSeconds = (metrics.last().timeInMilliseconds - metrics.first().timeInMilliseconds) / 1000.0
            if (elapsedTimeSeconds > 0) {
                // Calculate average speed in m/s (distance in meters / time in seconds)
                totalDistance / elapsedTimeSeconds
            } else {
                0.0
            }
        } else {
            0.0
        }

        // Calculate start and end times
        val startTime = if (metrics.isNotEmpty()) metrics.first().timeInMilliseconds else 0L
        val endTime = if (metrics.isNotEmpty()) metrics.last().timeInMilliseconds else 0L

        // Get device status (for satellite info)
        val deviceStatus = database.deviceStatusDao().getLastDeviceStatusByEvent(eventId)

        // Get maximum satellite count for this event
        val maxSatellites = database.deviceStatusDao().getMaxSatellitesForEvent(eventId) ?: 0

        // Get the latest weather data for this event instead of using dummy data
        val weather = database.weatherDao().getWeatherForEvent(eventId).lastOrNull()
            ?: Weather(
                weatherId = 0,
                eventId = eventId,
                weatherRestApi = "",
                temperature = 0f,
                windSpeed = 0f,
                windDirection = "N/A",
                relativeHumidity = 0
            )

        return EventWithDetails(
            event = event,
            weather = weather,
            locationPoints = locationPoints,
            laps = lapTimes,
            totalDistance = totalDistance,
            averageSpeed = averageSpeed,
            startTime = startTime,
            endTime = endTime,
            satellites = maxSatellites  // Use maximum satellite count
        )
    }

    fun loadMoreEvents() {
        loadEvents()
    }

    // Set search query and filter events
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        applySearchFilter(query)
    }

    // Filter events based on search query
    private fun applySearchFilter(query: String) {
        if (query.isEmpty()) {
            _filteredEventsWithDetails.value = allLoadedEvents.toList()
        } else {
            _filteredEventsWithDetails.value = allLoadedEvents.filter { event ->
                event.event.eventName.contains(query, ignoreCase = true) ||
                        event.event.artOfSport.contains(query, ignoreCase = true) ||
                        event.event.eventDate.contains(query, ignoreCase = true) ||
                        event.event.comment.contains(query, ignoreCase = true)
            }
        }
    }

    // Delete an event
    fun deleteEvent(eventId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // First get the event object
                val eventToDelete = database.eventDao().getEventById(eventId)

                // Delete the event if found
                if (eventToDelete != null) {
                    database.eventDao().deleteEvent(eventToDelete)

                    // Remove the event from our local lists
                    allLoadedEvents.removeAll { it.event.eventId == eventId }
                    _eventsWithDetails.value = allLoadedEvents.toList()

                    // Apply current search filter to update filtered list
                    applySearchFilter(_searchQuery.value)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Helper function to calculate lap times
    private fun calculateLapTimes(distanceTimePairs: List<Pair<Double, Long>>): List<Long> {
        val lapTimes = mutableListOf<Long>()
        var lastLapDistance = 0.0
        var lastLapTime = 0L

        if (distanceTimePairs.size < 2) {
            return lapTimes
        }

        // Initialize lastLapTime with the first timestamp
        lastLapTime = distanceTimePairs.first().second

        for (i in distanceTimePairs.indices) {
            val (distance, time) = distanceTimePairs[i]
            val lapDistance = 1000.0 // 1 km in meters

            // If we've reached or passed a new kilometer mark
            while (distance >= lastLapDistance + lapDistance) {
                lastLapDistance += lapDistance

                // Find the exact time for this lap using linear interpolation
                if (i > 0) {
                    val (prevDistance, prevTime) = distanceTimePairs[i-1]
                    val ratio = (lastLapDistance - prevDistance) / (distance - prevDistance)
                    val lapTime = prevTime + ((time - prevTime) * ratio).toLong()

                    // Add the time taken for this lap
                    val timeTaken = lapTime - lastLapTime
                    lapTimes.add(timeTaken)
                    lastLapTime = lapTime
                }
            }
        }
        return lapTimes
    }
}