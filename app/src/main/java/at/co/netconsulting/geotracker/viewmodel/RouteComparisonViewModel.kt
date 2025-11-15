package at.co.netconsulting.geotracker.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.co.netconsulting.geotracker.data.EventWithDetails
import at.co.netconsulting.geotracker.data.RouteComparison
import at.co.netconsulting.geotracker.data.RouteSimilarity
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.tools.RouteMatchingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint

class RouteComparisonViewModel(application: Application) : AndroidViewModel(application) {

    private val database = FitnessTrackerDatabase.getInstance(application)
    private val eventDao = database.eventDao()
    private val locationDao = database.locationDao()
    private val metricDao = database.metricDao()

    private val _similarRoutes = MutableStateFlow<List<RouteSimilarity>>(emptyList())
    val similarRoutes: StateFlow<List<RouteSimilarity>> = _similarRoutes.asStateFlow()

    private val _currentComparison = MutableStateFlow<RouteComparison?>(null)
    val currentComparison: StateFlow<RouteComparison?> = _currentComparison.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _eventName = MutableStateFlow("")
    val eventName: StateFlow<String> = _eventName.asStateFlow()

    /**
     * Find routes similar to the given event
     */
    fun findSimilarRoutes(eventId: Int) {
        android.util.Log.d("RouteComparisonVM", "Finding similar routes for eventId: $eventId")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                withContext(Dispatchers.IO) {
                    // Get reference event and its locations
                    val referenceEvent = eventDao.getEventById(eventId)
                    if (referenceEvent == null) {
                        _errorMessage.value = "Event not found"
                        return@withContext
                    }

                    // Set the event name
                    _eventName.value = referenceEvent.eventName

                    val referenceLocations = locationDao.getLocationsForEvent(eventId)
                    if (referenceLocations.isEmpty()) {
                        _errorMessage.value = "No route data found for this event"
                        return@withContext
                    }

                    // Calculate reference distance
                    val referenceMetrics = metricDao.getMetricsForEvent(eventId)
                    val referenceTotalDistance = referenceMetrics.lastOrNull()?.distance ?: 0.0

                    // Get all other events
                    val allEvents = eventDao.getEventsPaged(limit = 1000, offset = 0)
                        .filter { it.eventId != eventId && it.artOfSport == referenceEvent.artOfSport }

                    // Load locations for all candidate events
                    val candidateRoutes = allEvents.map { event ->
                        event.eventId to locationDao.getLocationsForEvent(event.eventId)
                    }

                    // Calculate distances for all candidates
                    val candidateDistances = allEvents.associate { event ->
                        val metrics = metricDao.getMetricsForEvent(event.eventId)
                        event.eventId to (metrics.lastOrNull()?.distance ?: 0.0)
                    }

                    // Find similar routes
                    val similarities = RouteMatchingUtils.findSimilarRoutes(
                        referenceLocations = referenceLocations,
                        referenceTotalDistance = referenceTotalDistance,
                        candidateRoutes = candidateRoutes,
                        candidateDistances = candidateDistances
                    )

                    // Enrich with event details
                    val enrichedSimilarities = similarities.map { similarity ->
                        val event = allEvents.find { it.eventId == similarity.eventId }
                        similarity.copy(
                            eventName = event?.eventName ?: "",
                            eventDate = event?.eventDate ?: ""
                        )
                    }

                    _similarRoutes.value = enrichedSimilarities
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error finding similar routes: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Compare two specific routes
     */
    fun compareRoutes(primaryEventId: Int, comparisonEventId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                withContext(Dispatchers.IO) {
                    // Load both events with full details
                    val primaryEventDetails = loadEventWithDetails(primaryEventId)
                    val comparisonEventDetails = loadEventWithDetails(comparisonEventId)

                    if (primaryEventDetails == null || comparisonEventDetails == null) {
                        _errorMessage.value = "Could not load event details"
                        return@withContext
                    }

                    // Load locations and metrics
                    val primaryLocations = locationDao.getLocationsForEvent(primaryEventId)
                    val comparisonLocations = locationDao.getLocationsForEvent(comparisonEventId)
                    val primaryMetrics = metricDao.getMetricsForEvent(primaryEventId)
                    val comparisonMetrics = metricDao.getMetricsForEvent(comparisonEventId)

                    // Find similarity score
                    val similarityScore = _similarRoutes.value
                        .find { it.eventId == comparisonEventId }?.similarityScore ?: 0.8

                    // Create detailed comparison
                    val comparison = RouteMatchingUtils.compareRoutes(
                        primaryEvent = primaryEventDetails,
                        comparisonEvent = comparisonEventDetails,
                        similarityScore = similarityScore,
                        primaryLocations = primaryLocations,
                        comparisonLocations = comparisonLocations,
                        primaryMetrics = primaryMetrics,
                        comparisonMetrics = comparisonMetrics
                    )
                    _currentComparison.value = comparison
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error comparing routes: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Load an event with full details
     */
    private suspend fun loadEventWithDetails(eventId: Int): EventWithDetails? {
        val event = eventDao.getEventById(eventId) ?: return null
        val metrics = metricDao.getMetricsForEvent(eventId)
        val locations = locationDao.getLocationsForEvent(eventId)

        if (metrics.isEmpty()) return null

        val totalDistance = metrics.lastOrNull()?.distance ?: 0.0
        val averageSpeed = metrics.map { it.speed.toDouble() }.average()
        val elevationGain = metrics.lastOrNull()?.elevationGain?.toDouble() ?: 0.0
        val startTime = metrics.firstOrNull()?.timeInMilliseconds ?: 0L
        val endTime = metrics.lastOrNull()?.timeInMilliseconds ?: 0L

        val avgHeartRate = metrics.filter { it.heartRate > 0 }
            .map { it.heartRate }
            .takeIf { it.isNotEmpty() }?.average()?.toInt() ?: 0

        val maxHeartRate = metrics.filter { it.heartRate > 0 }.maxOfOrNull { it.heartRate } ?: 0
        val minHeartRate = metrics.filter { it.heartRate > 0 }.minOfOrNull { it.heartRate } ?: 0

        val avgSlope = metrics.filter { it.slope != 0.0 }
            .map { it.slope }
            .takeIf { it.isNotEmpty() }?.average() ?: 0.0

        val locationPoints = locations.map { GeoPoint(it.latitude, it.longitude) }

        return EventWithDetails(
            event = event,
            hasFullDetails = true,
            locationPointCount = locations.size,
            totalDistance = totalDistance,
            averageSpeed = averageSpeed,
            elevationGain = elevationGain,
            startTime = startTime,
            endTime = endTime,
            avgHeartRate = avgHeartRate,
            maxHeartRate = maxHeartRate,
            minHeartRate = minHeartRate,
            averageSlope = avgSlope,
            locationPoints = locationPoints,
            metrics = metrics
        )
    }

    /**
     * Clear current comparison
     */
    fun clearComparison() {
        _currentComparison.value = null
        _errorMessage.value = null
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
}
