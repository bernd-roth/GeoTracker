package at.co.netconsulting.geotracker.viewmodel

import at.co.netconsulting.geotracker.data.EventWithDetails
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.EventMedia
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.sync.GeoTrackerApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

class EventsViewModel(
    private val database: FitnessTrackerDatabase,
    private val context: Context
) : ViewModel() {
    private val _eventsWithDetails = MutableStateFlow<List<EventWithDetails>>(emptyList())
    val eventsWithDetails: StateFlow<List<EventWithDetails>> = _eventsWithDetails.asStateFlow()

    private val _filteredEventsWithDetails = MutableStateFlow<List<EventWithDetails>>(emptyList())
    val filteredEventsWithDetails: StateFlow<List<EventWithDetails>> = _filteredEventsWithDetails.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Recording state monitoring
    private val _activeEventId = MutableStateFlow(-1)
    val activeEventId: StateFlow<Int> = _activeEventId.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    // Media state
    private val _mediaForEvent = MutableStateFlow<Map<Int, List<EventMedia>>>(emptyMap())
    val mediaForEvent: StateFlow<Map<Int, List<EventMedia>>> = _mediaForEvent.asStateFlow()

    private val _isLoadingMedia = MutableStateFlow<Set<Int>>(emptySet())
    val isLoadingMedia: StateFlow<Set<Int>> = _isLoadingMedia.asStateFlow()

    private val _isUploadingMedia = MutableStateFlow(false)
    val isUploadingMedia: StateFlow<Boolean> = _isUploadingMedia.asStateFlow()

    private val apiClient = GeoTrackerApiClient(context)

    val mediaCacheDir: File by lazy {
        File(context.cacheDir, "media_thumbnails").also { it.mkdirs() }
    }

    private var currentPage = 0

    // ADAPTIVE PAGE SIZES
    private val normalPageSize = 20      // Fast loading for normal browsing
    private val searchPageSize = 300     // Larger for search coverage
    private val maxPageSize = 1000       // Maximum for comprehensive search

    private var currentPageSize = normalPageSize
    private var hasMoreEvents = true
    private var isSearchMode = false

    // Date range filter
    private var startDateFilter: String? = null
    private var endDateFilter: String? = null

    // Sport type filter
    private var sportTypeFilter: String? = null

    private val _isDateFilterActive = MutableStateFlow(false)
    val isDateFilterActive: StateFlow<Boolean> = _isDateFilterActive.asStateFlow()

    init {
        // Monitor SharedPreferences for recording state changes
        // This runs in viewModelScope and will be automatically cancelled when ViewModel is cleared
        viewModelScope.launch {
            while (true) {
                val newActiveEventId = context.getSharedPreferences("CurrentEvent", Context.MODE_PRIVATE)
                    .getInt("active_event_id", -1)

                val newIsRecording = context.getSharedPreferences("RecordingState", Context.MODE_PRIVATE)
                    .getBoolean("is_recording", false)

                // Update state if changed
                if (newActiveEventId != _activeEventId.value || newIsRecording != _isRecording.value) {
                    Log.d("EventsViewModel", "Recording state changed: activeEventId=$newActiveEventId, isRecording=$newIsRecording")
                    _activeEventId.value = newActiveEventId
                    _isRecording.value = newIsRecording
                }

                delay(1000) // Check every 1 second
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        currentPage = 0

        viewModelScope.launch {
            val trimmedQuery = query.trim()

            if (trimmedQuery.isEmpty()) {
                // Exit search mode - return to normal pagination
                isSearchMode = false
                currentPageSize = normalPageSize
                loadEvents()
            } else {
                // Enter search mode - use larger page size
                isSearchMode = true
                currentPageSize = searchPageSize
                loadEventsWithCustomPageSize(searchPageSize)
            }
        }
    }

    private fun loadEventsWithCustomPageSize(customPageSize: Int) {
        if (_isLoading.value) return

        currentPage = 0
        hasMoreEvents = true
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val events = loadEventsWithDetails(0, customPageSize)
                _eventsWithDetails.value = events
                updateFilteredEvents()

                // Update hasMoreEvents based on results
                hasMoreEvents = events.size >= customPageSize
                currentPageSize = customPageSize

                Log.d("EventsViewModel", "Loaded ${events.size} events with page size $customPageSize")
            } catch (e: Exception) {
                Log.e("EventsViewModel", "Error loading events with custom page size: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun filterByDateRange(startDate: String? = null, endDate: String? = null) {
        startDateFilter = startDate
        endDateFilter = endDate
        sportTypeFilter = null
        currentPage = 0
        _isDateFilterActive.value = startDate != null && endDate != null

        viewModelScope.launch {
            if (startDate != null && endDate != null) {
                // When filtering by date range, load all events to ensure we don't miss any
                Log.d("EventsViewModel", "Loading all events for date range filter: $startDate to $endDate")
                loadEventsWithCustomPageSize(maxPageSize)
            } else {
                // Clear filter - return to normal pagination
                Log.d("EventsViewModel", "Clearing date filter, returning to normal pagination")
                isSearchMode = false
                currentPageSize = normalPageSize
                loadEvents()
            }
        }
    }

    fun filterByDateRangeAndSport(startDate: String, endDate: String, sportType: String) {
        startDateFilter = startDate
        endDateFilter = endDate
        sportTypeFilter = sportType
        currentPage = 0
        _isDateFilterActive.value = true

        viewModelScope.launch {
            Log.d("EventsViewModel", "Loading events for date range $startDate to $endDate, sport: $sportType")
            loadEventsWithCustomPageSize(maxPageSize)
        }
    }

    private suspend fun updateFilteredEvents() {
        val query = _searchQuery.value.lowercase().trim()
        val events = _eventsWithDetails.value

        val filtered = events.filter { event ->
            val matchesSearch = query.isEmpty() ||
                    event.event.eventName.lowercase().contains(query) ||
                    event.event.artOfSport.lowercase().contains(query) ||
                    event.event.comment.lowercase().contains(query) ||
                    event.event.eventDate.contains(query) // Also search by date

            val matchesDateRange = if (startDateFilter != null && endDateFilter != null) {
                isDateInRange(event.event.eventDate, startDateFilter!!, endDateFilter!!)
            } else {
                true
            }

            val matchesSportType = sportTypeFilter == null ||
                    event.event.artOfSport.equals(sportTypeFilter, ignoreCase = true)

            matchesSearch && matchesDateRange && matchesSportType
        }

        _filteredEventsWithDetails.value = filtered

        Log.d("EventsViewModel", "Filtered ${filtered.size} events from ${events.size} total (query: '$query', sport: '$sportTypeFilter')")
    }

    private fun isDateInRange(dateStr: String, startDateStr: String, endDateStr: String): Boolean {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = sdf.parse(dateStr)
            val startDate = sdf.parse(startDateStr)
            val endDate = sdf.parse(endDateStr)

            if (date != null && startDate != null && endDate != null) {
                val inRange = !date.before(startDate) && !date.after(endDate)
                Log.d("EventsViewModel", "Date check: $dateStr in range [$startDateStr, $endDateStr] = $inRange")
                return inRange
            }
        } catch (e: Exception) {
            Log.e("EventsViewModel", "Error parsing dates: ${e.message}")
        }
        return false
    }

    fun loadEvents() {
        if (_isLoading.value) return

        // If date filter is active, use larger page size to ensure filtered events are found
        if (_isDateFilterActive.value) {
            Log.d("EventsViewModel", "Date filter active, using max page size for loadEvents")
            loadEventsWithCustomPageSize(maxPageSize)
            return
        }

        currentPage = 0
        hasMoreEvents = true
        _isLoading.value = true
        currentPageSize = normalPageSize
        isSearchMode = false

        viewModelScope.launch {
            try {
                val events = loadEventsWithDetails(0, normalPageSize)
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
        // Don't allow pagination in search mode
        if (_isLoading.value || !hasMoreEvents || isSearchMode) return

        _isLoading.value = true
        currentPage++

        viewModelScope.launch {
            try {
                val newEvents = loadEventsWithDetails(currentPage * currentPageSize, currentPageSize)
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

    // Add a function to expand search if no results found
    fun expandSearchIfNeeded() {
        if (isSearchMode && _filteredEventsWithDetails.value.isEmpty() && currentPageSize < maxPageSize) {
            Log.d("EventsViewModel", "No search results found, expanding to larger page size")
            loadEventsWithCustomPageSize(maxPageSize)
        }
    }


    private suspend fun loadEventsWithDetails(offset: Int, limit: Int, loadLocationPoints: Boolean = false, loadFullDetails: Boolean = false): List<EventWithDetails> {
        return withContext(Dispatchers.IO) {
            try {
                val events = database.eventDao().getEventsPaged(limit, offset)

                processEvents(events, loadLocationPoints, loadFullDetails)
            } catch (e: Exception) {
                Log.e("EventsViewModel", "Error in loadEventsWithDetails: ${e.message}")
                emptyList()
            }
        }
    }

    private suspend fun processEvents(events: List<Event>, loadLocationPoints: Boolean = false, loadFullDetails: Boolean = false): List<EventWithDetails> {
        return events.map { event ->
            val eventId = event.eventId

            // ALWAYS load location point count (minimal data)
            val locationPointCount = database.locationDao().getLocationCountForEvent(eventId)

            // If NOT loading full details, return minimal EventWithDetails
            if (!loadFullDetails) {
                return@map EventWithDetails(
                    event = event,
                    hasFullDetails = false,
                    locationPointCount = locationPointCount
                )
            }

            // FULL DETAILS MODE - Calculate all statistics
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

            // Get time range FIRST - moved this up before avgSpeed calculation
            val timeRange = database.metricDao().getEventTimeRange(eventId)
            val startTime = timeRange?.minTime ?: 0
            val endTime = timeRange?.maxTime ?: 0

            // calculate average speed using the correct formula: total distance / total time
            val avgSpeed = if (startTime > 0 && endTime > startTime) {
                val durationSeconds = (endTime - startTime) / 1000.0
                val avgSpeedMps = totalDistance / durationSeconds
                avgSpeedMps * 3.6 // Convert m/s to km/h
            } else {
                0.0
            }

            // Calculate max and min speed from metrics
            val speeds = metrics.map { it.speed.toDouble() }
            val maxSpeed = speeds.maxOrNull() ?: 0.0

            // Get elevation data
            val elevations = metrics.map { it.elevation.toDouble() }
            val maxElevation = elevations.maxOrNull() ?: 0.0
            val minElevation = if (elevations.isNotEmpty()) elevations.minOrNull() ?: 0.0 else 0.0

            // Calculate total elevation gain and loss
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

            // Get maximum elevation gain value - check stored values first, then calculate
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

            // EFFICIENT SATELLITE CALCULATION: Get all satellite data in one database call
            val (minSatellites, maxSatellites, avgSatellites) = try {
                val satelliteCountStrings = database.deviceStatusDao().getSatelliteCountsForEvent(eventId)
                val satelliteCounts = satelliteCountStrings.mapNotNull { it.toIntOrNull() }.filter { it > 0 }

                if (satelliteCounts.isNotEmpty()) {
                    val min = satelliteCounts.minOrNull() ?: 0
                    val max = satelliteCounts.maxOrNull() ?: 0
                    val avg = (satelliteCounts.sum().toDouble() / satelliteCounts.size).toInt()
                    Triple(min, max, avg)
                } else {
                    Triple(0, 0, 0)
                }
            } catch (e: Exception) {
                Log.w("EventsViewModel", "Could not get satellite data for event $eventId: ${e.message}")
                Triple(0, 0, 0)
            }

            // Calculate slope statistics
            val (averageSlope, maxSlope, minSlope) = calculateSlope(metrics)

            // Get weather and other data
            val weather = database.eventDao().getLatestWeatherForEvent(eventId)

            // Load actual location points if requested
            val geoPoints = if (loadLocationPoints) {
                val locations = database.locationDao().getLocationsForEvent(eventId)
                locations.map { GeoPoint(it.latitude, it.longitude) }
            } else {
                emptyList()
            }

            val lapTimeObjects = try {
                database.lapTimeDao().getLapTimesForEvent(eventId)
            } catch (e: Exception) {
                emptyList<at.co.netconsulting.geotracker.domain.LapTime>()
            }
            val lapTimes = lapTimeObjects.map { it.endTime - it.startTime }

            // Load discipline transitions for multisport events
            val isMultisport = event.artOfSport.equals("Triathlon", ignoreCase = true) ||
                    event.artOfSport.equals("Ultratriathlon", ignoreCase = true) ||
                    event.artOfSport.equals("Duathlon", ignoreCase = true)
            val disciplineTransitions = if (isMultisport) {
                try {
                    database.disciplineTransitionDao().getTransitionsForEvent(eventId)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            // Create and return EventWithDetails with full details
            EventWithDetails(
                event = event,
                hasFullDetails = true,
                totalDistance = totalDistance,
                maxSpeed = maxSpeed,
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
                locationPointCount = locationPointCount,
                // Updated satellite fields
                minSatellites = minSatellites,
                maxSatellites = maxSatellites,
                avgSatellites = avgSatellites,
                minHeartRate = minHeartRate,
                maxHeartRate = maxHeartRate,
                avgHeartRate = avgHeartRate,
                heartRateDevice = heartRateDevice,
                // Slope statistics
                averageSlope = averageSlope,
                maxSlope = maxSlope,
                minSlope = minSlope,
                metrics = metrics,
                // Multisport discipline grouping
                lapTimeDetails = lapTimeObjects,
                disciplineTransitions = disciplineTransitions
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
                    val events = loadEventsWithDetails(0, (currentPage + 1) * currentPageSize)
                    _eventsWithDetails.value = events
                    updateFilteredEvents()
                } else {
                    Log.w("EventsViewModel", "Event with ID $eventId not found for deletion")
                }
            } catch (e: Exception) {
                Log.e("EventsViewModel", "Error deleting event: ${e.message}")
            }
        }
    }

    /**
     * Load full details for a specific event (on-demand when user expands event card)
     * This replaces the minimal event data with full statistics
     */
    suspend fun loadFullDetailsForEvent(eventId: Int): EventWithDetails? {
        return withContext(Dispatchers.IO) {
            try {
                // Get the event from database
                val event = database.eventDao().getEventById(eventId)
                if (event != null) {
                    // Process this single event with full details
                    val eventWithDetails = processEvents(listOf(event), loadLocationPoints = false, loadFullDetails = true).firstOrNull()

                    if (eventWithDetails != null) {
                        // Update the event in our lists
                        _eventsWithDetails.value = _eventsWithDetails.value.map {
                            if (it.event.eventId == eventId) eventWithDetails else it
                        }
                        updateFilteredEvents()

                        Log.d("EventsViewModel", "Loaded full details for event $eventId")
                    }

                    eventWithDetails
                } else {
                    Log.w("EventsViewModel", "Event with ID $eventId not found")
                    null
                }
            } catch (e: Exception) {
                Log.e("EventsViewModel", "Error loading full details for event $eventId: ${e.message}")
                null
            }
        }
    }

    // Debug function to check what's in an imported event
    fun debugImportedEvent(eventId: Int) {
        viewModelScope.launch {
            val event = _eventsWithDetails.value.find { it.event.eventId == eventId }
            if (event != null) {
                Log.d("ImportedEventDebug", "=== IMPORTED EVENT DEBUG ===")
                Log.d("ImportedEventDebug", "Event ID: ${event.event.eventId}")
                Log.d("ImportedEventDebug", "Event Name: '${event.event.eventName}'")
                Log.d("ImportedEventDebug", "Art of Sport: '${event.event.artOfSport}'")
                Log.d("ImportedEventDebug", "Comment: '${event.event.comment}'")
                Log.d("ImportedEventDebug", "Event Date: '${event.event.eventDate}'")
                Log.d("ImportedEventDebug", "=== END DEBUG ===")
            } else {
                Log.d("ImportedEventDebug", "Event with ID $eventId not found in loaded events")
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

    private fun calculateSlope(metrics: List<at.co.netconsulting.geotracker.domain.Metric>): Triple<Double, Double, Double> {
        if (metrics.size < 2) {
            return Triple(0.0, 0.0, 0.0)
        }

        // Sort metrics by time to ensure correct order
        val sortedMetrics = metrics.sortedBy { it.timeInMilliseconds }

        // Use O(1) memory by tracking only min, max, sum, and count
        var minSlope = Double.MAX_VALUE
        var maxSlope = Double.MIN_VALUE
        var slopeSum = 0.0
        var slopeCount = 0
        var totalDistance = 0.0

        // Calculate slope between consecutive points
        for (i in 1 until sortedMetrics.size) {
            val previousMetric = sortedMetrics[i - 1]
            val currentMetric = sortedMetrics[i]

            // Calculate actual distance between points (not cumulative distance)
            val distanceDiff = if (currentMetric.distance > previousMetric.distance) {
                // If distance is cumulative, use the difference
                currentMetric.distance - previousMetric.distance
            } else {
                // If distance resets or is not cumulative, calculate from stored distance
                currentMetric.distance
            }

            // Use GPS elevation for slope calculations
            val currentElevation = currentMetric.elevation.toDouble()
            val previousElevation = previousMetric.elevation.toDouble()

            val elevationDiff = currentElevation - previousElevation

            // Only calculate slope if there's meaningful distance covered (> 2 meters)
            // With 1-second recording intervals, running speeds of 10-15 km/h yield 2.8-4.2 m/s
            // Using 2m threshold ensures we capture most GPS points while filtering stationary noise
            if (distanceDiff > 2.0) {
                // Slope = (elevation change / distance change) * 100 to get percentage
                val slope = (elevationDiff / distanceDiff) * 100.0

                // More reasonable filtering - allow steeper slopes but filter extreme GPS errors
                if (slope >= -50.0 && slope <= 50.0) {
                    // Update min and max
                    if (slope < minSlope) minSlope = slope
                    if (slope > maxSlope) maxSlope = slope

                    // Update sum and count for average
                    slopeSum += slope
                    slopeCount++
                    totalDistance += distanceDiff
                }
            }
        }

        return if (slopeCount > 0 && totalDistance > 0) {
            // Calculate average slope as mean of all individual slopes
            // This gives meaningful results for loops/circular routes (unlike net elevation / distance)
            val avgSlope = slopeSum / slopeCount

            // Log for debugging - remove in production
            android.util.Log.d("SlopeCalculation", "Slopes count: $slopeCount, Total distance: $totalDistance m")
            android.util.Log.d("SlopeCalculation", "Average slope: $avgSlope%, Min: $minSlope%, Max: $maxSlope%")

            Triple(avgSlope, maxSlope, minSlope)
        } else {
            Triple(0.0, 0.0, 0.0)
        }
    }

    // ==================== Media Methods ====================

    /**
     * Load media for a specific event from local database and optionally sync with server
     */
    fun loadMediaForEvent(eventId: Int, sessionId: String? = null) {
        // Mark as loading
        _isLoadingMedia.value = _isLoadingMedia.value + eventId

        viewModelScope.launch {
            try {
                // First, load from local database (this works for both uploaded and non-uploaded events)
                val localMedia = database.eventMediaDao().getMediaForEvent(eventId)
                _mediaForEvent.value = _mediaForEvent.value + (eventId to localMedia)

                // If event is uploaded, also sync with server
                if (!sessionId.isNullOrBlank()) {
                    val result = apiClient.getSessionMedia(sessionId)
                    result.onSuccess { serverMediaList ->
                        withContext(Dispatchers.IO) {
                            // Sync server media to local database
                            serverMediaList.forEach { serverMedia ->
                                val existingMedia = database.eventMediaDao().getMediaByUuid(serverMedia.mediaUuid)
                                if (existingMedia == null) {
                                    // Insert new media from server
                                    val newMedia = EventMedia(
                                        eventId = eventId,
                                        mediaUuid = serverMedia.mediaUuid,
                                        mediaType = serverMedia.mediaType,
                                        fileExtension = serverMedia.fileExtension,
                                        thumbnailUrl = serverMedia.thumbnailUrl,
                                        fullUrl = serverMedia.fullUrl,
                                        caption = serverMedia.caption,
                                        sortOrder = serverMedia.sortOrder,
                                        isUploaded = true,
                                        fileSizeBytes = serverMedia.fileSizeBytes
                                    )
                                    database.eventMediaDao().insertMedia(newMedia)
                                } else {
                                    // Update existing media with server URLs
                                    database.eventMediaDao().updateMediaUploadStatus(
                                        existingMedia.mediaId,
                                        true,
                                        serverMedia.thumbnailUrl,
                                        serverMedia.fullUrl
                                    )
                                }
                            }

                            // Reload from database
                            val updatedMedia = database.eventMediaDao().getMediaForEvent(eventId)
                            _mediaForEvent.value = _mediaForEvent.value + (eventId to updatedMedia)

                            // Download thumbnails that aren't cached
                            updatedMedia.forEach { media ->
                                if (media.thumbnailUrl != null && media.localThumbnailPath == null) {
                                    downloadAndCacheThumbnail(media)
                                }
                            }
                        }
                    }.onFailure { error ->
                        Log.e("EventsViewModel", "Error fetching media from server: ${error.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("EventsViewModel", "Error loading media for event $eventId: ${e.message}")
            } finally {
                _isLoadingMedia.value = _isLoadingMedia.value - eventId
            }
        }
    }

    /**
     * Download and cache thumbnail locally
     */
    private suspend fun downloadAndCacheThumbnail(media: EventMedia) {
        if (media.thumbnailUrl == null) return

        try {
            val result = apiClient.downloadThumbnail(media.thumbnailUrl!!, mediaCacheDir)
            result.onSuccess { cachedFile ->
                database.eventMediaDao().updateLocalThumbnailPath(media.mediaId, cachedFile.absolutePath)
                // Update the media list in memory
                val eventMedia = _mediaForEvent.value[media.eventId]
                if (eventMedia != null) {
                    val updatedList = eventMedia.map {
                        if (it.mediaId == media.mediaId) it.copy(localThumbnailPath = cachedFile.absolutePath) else it
                    }
                    _mediaForEvent.value = _mediaForEvent.value + (media.eventId to updatedList)
                }
            }
        } catch (e: Exception) {
            Log.e("EventsViewModel", "Error downloading thumbnail: ${e.message}")
        }
    }

    /**
     * Upload media file for an event.
     * If the event is not yet uploaded, media is stored locally and will be uploaded
     * when the event is synced to the server.
     */
    fun uploadMediaForEvent(eventId: Int, uri: Uri) {
        val event = _eventsWithDetails.value.find { it.event.eventId == eventId }?.event
        val sessionId = event?.sessionId

        _isUploadingMedia.value = true

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Copy file to local storage
                    val contentResolver = context.contentResolver
                    val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                    val mediaType = if (mimeType.startsWith("video")) "video" else "image"
                    val extension = when {
                        mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                        mimeType.contains("png") -> "png"
                        mimeType.contains("heic") || mimeType.contains("heif") -> "heic"
                        mimeType.contains("mp4") -> "mp4"
                        mimeType.contains("quicktime") -> "mov"
                        else -> "jpg"
                    }

                    // Store in persistent media directory (not temp cache)
                    val mediaDir = File(context.filesDir, "event_media/$eventId").also { it.mkdirs() }
                    val mediaUuid = UUID.randomUUID().toString()
                    val localFile = File(mediaDir, "$mediaUuid.$extension")

                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(localFile).use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Generate local thumbnail for images
                    var localThumbnailPath: String? = null
                    if (mediaType == "image") {
                        localThumbnailPath = generateLocalThumbnail(localFile, mediaUuid)
                    }

                    // Create local media record
                    val localMedia = EventMedia(
                        eventId = eventId,
                        mediaUuid = mediaUuid,
                        mediaType = mediaType,
                        fileExtension = extension,
                        localFilePath = localFile.absolutePath,
                        localThumbnailPath = localThumbnailPath,
                        isUploaded = false,
                        fileSizeBytes = localFile.length()
                    )
                    val mediaId = database.eventMediaDao().insertMedia(localMedia).toInt()

                    Log.d("EventsViewModel", "Media saved locally: $mediaUuid")

                    // If event is already uploaded, upload media to server immediately
                    if (!sessionId.isNullOrBlank()) {
                        val result = apiClient.uploadMedia(sessionId, localFile, mediaType)
                        result.onSuccess { uploadResult ->
                            // Update local record with server data
                            database.eventMediaDao().updateMediaUploadStatus(
                                mediaId,
                                true,
                                uploadResult.thumbnailUrl,
                                uploadResult.fullUrl
                            )

                            // Update the UUID to match server
                            val updatedMedia = database.eventMediaDao().getMediaById(mediaId)
                            if (updatedMedia != null) {
                                database.eventMediaDao().updateMedia(
                                    updatedMedia.copy(mediaUuid = uploadResult.mediaUuid)
                                )
                            }

                            Log.d("EventsViewModel", "Media uploaded to server: ${uploadResult.mediaUuid}")
                        }.onFailure { error ->
                            Log.e("EventsViewModel", "Media server upload failed (kept locally): ${error.message}")
                            // Keep local record - will retry on next sync
                        }
                    } else {
                        Log.d("EventsViewModel", "Event not yet uploaded - media stored locally for later sync")
                    }

                    // Update media list in memory
                    val updatedMedia = database.eventMediaDao().getMediaForEvent(eventId)
                    _mediaForEvent.value = _mediaForEvent.value + (eventId to updatedMedia)
                }
            } catch (e: Exception) {
                Log.e("EventsViewModel", "Error saving media: ${e.message}")
            } finally {
                _isUploadingMedia.value = false
            }
        }
    }

    /**
     * Generate a local thumbnail for an image file
     */
    private fun generateLocalThumbnail(imageFile: File, mediaUuid: String): String? {
        return try {
            val thumbnailFile = File(mediaCacheDir, "${mediaUuid}_thumb.jpg")

            // Use Android's built-in thumbnail generation
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, options)

            // Calculate sample size for ~400px thumbnail
            val maxSize = 400
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxSize || options.outHeight / sampleSize > maxSize) {
                sampleSize *= 2
            }

            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize

            val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath, options)
            if (bitmap != null) {
                FileOutputStream(thumbnailFile).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                }
                bitmap.recycle()
                thumbnailFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("EventsViewModel", "Error generating local thumbnail: ${e.message}")
            null
        }
    }

    /**
     * Upload pending media for an event after the event has been uploaded to the server.
     * Call this after successfully uploading an event.
     */
    suspend fun uploadPendingMediaForEvent(eventId: Int, sessionId: String) {
        withContext(Dispatchers.IO) {
            try {
                val pendingMedia = database.eventMediaDao().getPendingUploadsForEvent(eventId)
                if (pendingMedia.isEmpty()) {
                    Log.d("EventsViewModel", "No pending media to upload for event $eventId")
                    return@withContext
                }

                Log.d("EventsViewModel", "Uploading ${pendingMedia.size} pending media for event $eventId")

                pendingMedia.forEach { media ->
                    val localFile = media.localFilePath?.let { File(it) }
                    if (localFile != null && localFile.exists()) {
                        val result = apiClient.uploadMedia(sessionId, localFile, media.mediaType)
                        result.onSuccess { uploadResult ->
                            database.eventMediaDao().updateMediaUploadStatus(
                                media.mediaId,
                                true,
                                uploadResult.thumbnailUrl,
                                uploadResult.fullUrl
                            )
                            database.eventMediaDao().updateMedia(
                                media.copy(mediaUuid = uploadResult.mediaUuid, isUploaded = true)
                            )
                            Log.d("EventsViewModel", "Pending media uploaded: ${uploadResult.mediaUuid}")
                        }.onFailure { error ->
                            Log.e("EventsViewModel", "Failed to upload pending media ${media.mediaUuid}: ${error.message}")
                        }
                    } else {
                        Log.w("EventsViewModel", "Local file not found for media ${media.mediaUuid}")
                    }
                }

                // Refresh media list
                val updatedMedia = database.eventMediaDao().getMediaForEvent(eventId)
                _mediaForEvent.value = _mediaForEvent.value + (eventId to updatedMedia)

            } catch (e: Exception) {
                Log.e("EventsViewModel", "Error uploading pending media: ${e.message}")
            }
        }
    }

    /**
     * Delete media from server and local database
     */
    fun deleteMedia(media: EventMedia) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Delete from server if uploaded
                    if (media.isUploaded) {
                        val result = apiClient.deleteMedia(media.mediaUuid)
                        result.onFailure { error ->
                            Log.e("EventsViewModel", "Failed to delete media from server: ${error.message}")
                        }
                    }

                    // Delete local cached thumbnail
                    media.localThumbnailPath?.let { path ->
                        File(path).delete()
                    }

                    // Delete local file if exists
                    media.localFilePath?.let { path ->
                        File(path).delete()
                    }

                    // Delete from database
                    database.eventMediaDao().deleteMedia(media)

                    // Update state
                    val eventMedia = _mediaForEvent.value[media.eventId]
                    if (eventMedia != null) {
                        val updatedList = eventMedia.filter { it.mediaId != media.mediaId }
                        _mediaForEvent.value = _mediaForEvent.value + (media.eventId to updatedList)
                    }

                    Log.d("EventsViewModel", "Media deleted: ${media.mediaUuid}")
                }
            } catch (e: Exception) {
                Log.e("EventsViewModel", "Error deleting media: ${e.message}")
            }
        }
    }

    /**
     * Get media count for an event (used in minimal loading)
     */
    suspend fun getMediaCountForEvent(eventId: Int): Int {
        return withContext(Dispatchers.IO) {
            database.eventMediaDao().getMediaCountForEvent(eventId)
        }
    }

}

// ViewModelFactory to provide database and context to EventsViewModel
class EventsViewModelFactory(
    private val database: FitnessTrackerDatabase,
    private val context: Context
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(EventsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return EventsViewModel(database, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}