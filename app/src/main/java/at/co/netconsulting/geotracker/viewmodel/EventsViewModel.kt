package at.co.netconsulting.geotracker.viewmodel

import EventWithDetails
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

            // Calculate heart rate statistics (keep existing code)
            val metrics = database.metricDao().getMetricsForEvent(eventId)
            val heartRates = metrics.filter { it.heartRate > 0 }.map { it.heartRate }
            val minHeartRate = heartRates.minOrNull() ?: 0
            val maxHeartRate = heartRates.maxOrNull() ?: 0
            val avgHeartRate = if (heartRates.isNotEmpty())
                heartRates.sum() / heartRates.size
            else 0

            // Keep existing heart rate device name code
            val heartRateDevice = metrics.firstOrNull {
                it.heartRate > 0 && it.heartRateDevice.isNotEmpty() && it.heartRateDevice != "None"
            }?.heartRateDevice ?: ""

            // Calculate basic metrics (keep existing code)
            val totalDistance = metrics.maxByOrNull { it.distance }?.distance ?: 0.0
            val avgSpeed = if (metrics.isNotEmpty()) metrics.sumOf { it.speed.toDouble() } / metrics.size else 0.0

            // Get elevation data (keep existing code)
            val elevations = metrics.map { it.elevation.toDouble() }
            val maxElevation = elevations.maxOrNull() ?: 0.0
            val minElevation = if (elevations.isNotEmpty()) elevations.minOrNull() ?: 0.0 else 0.0

            // Calculate total elevation gain and loss (keep existing code)
            var totalElevationGain = 0.0
            var totalElevationLoss = 0.0

            // Use the actual elevation data points to calculate gain and loss properly
            if (metrics.size > 1) {
                for (i in 1 until metrics.size) {
                    val currentElevation = metrics[i].elevation.toDouble()
                    val previousElevation = metrics[i-1].elevation.toDouble()
                    val elevationDiff = currentElevation - previousElevation

                    if (elevationDiff > 0) {
                        totalElevationGain += elevationDiff
                    } else if (elevationDiff < 0) {
                        totalElevationLoss += -elevationDiff  // Make positive for display
                    }
                }
            }

            // MODIFIED: Get maximum elevation gain value - check stored values first, then calculate
            val storedMaxElevationGain = metrics.maxByOrNull { it.elevationGain }?.elevationGain?.toDouble() ?: 0.0

            // If stored elevation gain is zero but we have elevation data, calculate it
            val maxElevationGain = if (storedMaxElevationGain > 0.0) {
                // Use stored value if available
                storedMaxElevationGain
            } else if (metrics.size > 1) {
                // Calculate max elevation gain from consecutive points
                var maxGain = 0.0
                var currentGain = 0.0

                // Find max consecutive elevation increase
                for (i in 1 until metrics.size) {
                    val diff = metrics[i].elevation - metrics[i-1].elevation

                    if (diff > 0) {
                        // Add to current gain
                        currentGain += diff
                    } else {
                        // Reset current gain if elevation decreases
                        currentGain = 0.0
                    }

                    // Update max gain if current gain is larger
                    if (currentGain > maxGain) {
                        maxGain = currentGain
                    }
                }

                maxGain
            } else {
                0.0
            }

            // Get time range (keep existing code)
            val timeRange = database.metricDao().getEventTimeRange(eventId)
            val startTime = timeRange?.minTime ?: 0
            val endTime = timeRange?.maxTime ?: 0

            // Rest of your existing code...
            val weather = database.eventDao().getLatestWeatherForEvent(eventId)
            val locations = database.locationDao().getLocationsForEvent(eventId)
            val geoPoints = locations.map { GeoPoint(it.latitude, it.longitude) }
            val lapTimes = try {
                database.lapTimeDao().getLapTimesForEvent(eventId).map { it.endTime - it.startTime }
            } catch (e: Exception) {
                emptyList<Long>()
            }
            val deviceStatus = database.deviceStatusDao().getLastDeviceStatusByEvent(eventId)
            val satellites = deviceStatus?.numberOfSatellites?.toIntOrNull() ?: 0

            EventWithDetails(
                event = event,
                totalDistance = totalDistance,
                averageSpeed = avgSpeed,
                maxElevation = maxElevation,
                minElevation = minElevation,
                maxElevationGain = maxElevationGain,
                elevationGain = totalElevationGain,
                elevationLoss = totalElevationLoss,
                startTime = startTime,
                endTime = endTime,
                weather = weather,
                laps = lapTimes,
                locationPoints = geoPoints,
                satellites = satellites,
                minHeartRate = minHeartRate,
                maxHeartRate = maxHeartRate,
                avgHeartRate = avgHeartRate,
                heartRateDevice = heartRateDevice,
                metrics = metrics
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

    fun debugElevationData(eventId: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // Get metrics for this specific event
                    val metrics = database.metricDao().getMetricsForEvent(eventId)

                    Log.d("ElevationDebug", "=== ELEVATION DEBUG FOR EVENT ID: $eventId ===")
                    Log.d("ElevationDebug", "Total metric points: ${metrics.size}")

                    // Check if we have any elevation data
                    val elevations = metrics.map { it.elevation.toDouble() }
                    val hasElevationData = elevations.isNotEmpty()
                    Log.d("ElevationDebug", "Has elevation data: $hasElevationData")

                    if (hasElevationData) {
                        val minElevation = elevations.minOrNull() ?: 0.0
                        val maxElevation = elevations.maxOrNull() ?: 0.0
                        Log.d("ElevationDebug", "Min elevation: $minElevation m")
                        Log.d("ElevationDebug", "Max elevation: $maxElevation m")
                        Log.d("ElevationDebug", "Elevation range: ${maxElevation - minElevation} m")

                        // Check for elevation gain field usage
                        val elevationGainValues = metrics.map { it.elevationGain }
                        val nonZeroGainValues = elevationGainValues.filter { it > 0 }
                        Log.d("ElevationDebug", "Points with non-zero elevationGain: ${nonZeroGainValues.size}")
                        Log.d("ElevationDebug", "Max recorded elevationGain: ${elevationGainValues.maxOrNull() ?: 0}")

                        // Calculate the actual elevation gain from point-to-point analysis
                        var calculatedGain = 0.0
                        var calculatedLoss = 0.0

                        for (i in 1 until metrics.size) {
                            val diff = metrics[i].elevation - metrics[i-1].elevation
                            if (diff > 0) {
                                calculatedGain += diff
                            } else {
                                calculatedLoss += -diff
                            }
                        }

                        Log.d("ElevationDebug", "Calculated elevation gain: $calculatedGain m")
                        Log.d("ElevationDebug", "Calculated elevation loss: $calculatedLoss m")

                        // Log a few sample points to check data quality
                        if (metrics.size > 0) {
                            Log.d("ElevationDebug", "--- Sample data points ---")
                            val indicesToLog = listOf(0, metrics.size / 2, metrics.size - 1).distinct()
                            indicesToLog.forEach { idx ->
                                if (idx < metrics.size) {
                                    val m = metrics[idx]
                                    Log.d("ElevationDebug", "Point $idx: time=${m.timeInMilliseconds}, elev=${m.elevation}, gain=${m.elevationGain}")
                                }
                            }
                        }
                    }

                    // Check event details
                    val event = database.eventDao().getEventById(eventId)
                    val eventName = event?.eventName ?: "Unknown"
                    Log.d("ElevationDebug", "Event name: $eventName")
                    Log.d("ElevationDebug", "=== END ELEVATION DEBUG ===")

                } catch (e: Exception) {
                    Log.e("ElevationDebug", "Error debugging elevation data", e)
                }
            }
        }
    }
}