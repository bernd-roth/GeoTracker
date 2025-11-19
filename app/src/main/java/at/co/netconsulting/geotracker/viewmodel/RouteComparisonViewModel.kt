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

    // Live comparison progress
    private val _comparisonProgress = MutableStateFlow<ComparisonProgress?>(null)
    val comparisonProgress: StateFlow<ComparisonProgress?> = _comparisonProgress.asStateFlow()

    data class ComparisonProgress(
        val currentEventId: Int,
        val currentEventName: String,
        val currentIndex: Int,
        val totalCandidates: Int,
        val matchesFound: List<RouteSimilarity>
    )

    /**
     * Find routes similar to the given event
     * Emits live progress updates as routes are compared
     */
    fun findSimilarRoutes(eventId: Int) {
        android.util.Log.d("RouteComparisonVM", "Finding similar routes for eventId: $eventId")
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _comparisonProgress.value = null

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

                    android.util.Log.d("RouteComparisonVM", "Comparing '${referenceEvent.eventName}' against ${allEvents.size} candidates")

                    val referenceStart = referenceLocations.first().toGeoLocation()
                    val referenceEnd = referenceLocations.last().toGeoLocation()
                    val matchesFound = mutableListOf<RouteSimilarity>()

                    // Process each candidate event one by one and emit progress
                    allEvents.forEachIndexed { index, candidateEvent ->
                        // Emit progress update
                        _comparisonProgress.value = ComparisonProgress(
                            currentEventId = candidateEvent.eventId,
                            currentEventName = candidateEvent.eventName,
                            currentIndex = index + 1,
                            totalCandidates = allEvents.size,
                            matchesFound = matchesFound.toList()
                        )

                        // Load locations and metrics for this candidate
                        val candidateLocations = locationDao.getLocationsForEvent(candidateEvent.eventId)
                        if (candidateLocations.isEmpty()) return@forEachIndexed

                        val candidateMetrics = metricDao.getMetricsForEvent(candidateEvent.eventId)
                        val candidateDistance = candidateMetrics.lastOrNull()?.distance ?: 0.0

                        // Check event name similarity first (optimization)
                        val nameSimilarity = calculateEventNameSimilarity(referenceEvent.eventName, candidateEvent.eventName)
                        if (nameSimilarity < 0.9) {
                            android.util.Log.d("RouteComparisonVM", "Event ${candidateEvent.eventId} rejected: name similarity ${String.format("%.2f", nameSimilarity)} < 0.9")
                            return@forEachIndexed
                        }

                        // Perform route comparison
                        val similarity = RouteMatchingUtils.findSimilarRoutes(
                            referenceLocations = referenceLocations,
                            referenceTotalDistance = referenceTotalDistance,
                            candidateRoutes = listOf(candidateEvent.eventId to candidateLocations),
                            candidateDistances = mapOf(candidateEvent.eventId to candidateDistance),
                            referenceEventName = referenceEvent.eventName,
                            candidateEventNames = mapOf(candidateEvent.eventId to candidateEvent.eventName)
                        ).firstOrNull()

                        // If match found, add to results
                        if (similarity != null) {
                            val enrichedSimilarity = similarity.copy(
                                eventName = candidateEvent.eventName,
                                eventDate = candidateEvent.eventDate
                            )
                            matchesFound.add(enrichedSimilarity)

                            // Update progress with new match
                            _comparisonProgress.value = ComparisonProgress(
                                currentEventId = candidateEvent.eventId,
                                currentEventName = candidateEvent.eventName,
                                currentIndex = index + 1,
                                totalCandidates = allEvents.size,
                                matchesFound = matchesFound.toList()
                            )
                        }
                    }

                    _similarRoutes.value = matchesFound.sortedByDescending { it.similarityScore }
                }
            } catch (e: Exception) {
                _errorMessage.value = "Error finding similar routes: ${e.message}"
            } finally {
                _isLoading.value = false
                _comparisonProgress.value = null
            }
        }
    }

    /**
     * Helper function to calculate event name similarity
     */
    private fun calculateEventNameSimilarity(name1: String, name2: String): Double {
        val normalized1 = name1.lowercase().replace("[^a-z0-9\\s]".toRegex(), "")
        val normalized2 = name2.lowercase().replace("[^a-z0-9\\s]".toRegex(), "")

        if (normalized1 == normalized2) return 1.0
        if (normalized1.isEmpty() || normalized2.isEmpty()) return 0.0

        val tokens1 = normalized1.split("\\s+".toRegex()).filter { it.isNotEmpty() }.sorted()
        val tokens2 = normalized2.split("\\s+".toRegex()).filter { it.isNotEmpty() }.sorted()

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0

        val sortedTokens1 = tokens1.joinToString(" ")
        val sortedTokens2 = tokens2.joinToString(" ")

        if (sortedTokens1 == sortedTokens2) return 1.0

        val distance = levenshteinDistance(sortedTokens1, sortedTokens2)
        val maxLength = Math.max(sortedTokens1.length, sortedTokens2.length)

        return 1.0 - (distance.toDouble() / maxLength)
    }

    /**
     * Helper function to calculate Levenshtein distance
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val matrix = Array(len1 + 1) { IntArray(len2 + 1) }

        for (i in 0..len1) matrix[i][0] = i
        for (j in 0..len2) matrix[0][j] = j

        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                matrix[i][j] = minOf(
                    matrix[i - 1][j] + 1,
                    matrix[i][j - 1] + 1,
                    matrix[i - 1][j - 1] + cost
                )
            }
        }

        return matrix[len1][len2]
    }

    /**
     * Helper extension function
     */
    private fun Location.toGeoLocation(): at.co.netconsulting.geotracker.data.GeoLocation {
        return at.co.netconsulting.geotracker.data.GeoLocation(latitude, longitude, altitude)
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
