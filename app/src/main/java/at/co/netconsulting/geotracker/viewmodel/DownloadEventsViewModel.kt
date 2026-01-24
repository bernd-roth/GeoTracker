package at.co.netconsulting.geotracker.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.co.netconsulting.geotracker.domain.DeviceStatus
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.LapTime
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.domain.Weather
import at.co.netconsulting.geotracker.sync.GeoTrackerApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class DownloadEventsViewModel(application: Application) : AndroidViewModel(application) {
    private val database = FitnessTrackerDatabase.getInstance(application)
    private val apiClient = GeoTrackerApiClient(application)

    private val _availableSessions = MutableStateFlow<List<GeoTrackerApiClient.RemoteSessionSummary>>(emptyList())
    val availableSessions: StateFlow<List<GeoTrackerApiClient.RemoteSessionSummary>> = _availableSessions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadState>> = _downloadProgress.asStateFlow()

    private val _selectedSessions = MutableStateFlow<Set<String>>(emptySet())
    val selectedSessions: StateFlow<Set<String>> = _selectedSessions.asStateFlow()

    sealed class DownloadState {
        object Idle : DownloadState()
        object Checking : DownloadState()
        object ReadyToDownload : DownloadState()
        object AlreadyDownloaded : DownloadState()
        object Downloading : DownloadState()
        data class Success(val message: String) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    companion object {
        private const val TAG = "DownloadEventsViewModel"
    }

    init {
        Log.d(TAG, "DownloadEventsViewModel initialized")
        loadAvailableSessions()
    }

    fun loadAvailableSessions() {
        Log.d(TAG, "loadAvailableSessions() called")
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "Starting to fetch sessions from server...")

                // Fetch ALL sessions from server without filtering by user
                val result = withContext(Dispatchers.IO) {
                    Log.d(TAG, "Calling apiClient.listUserSessions()")
                    apiClient.listUserSessions(filterByUser = false)
                }

                result.fold(
                    onSuccess = { sessions ->
                        // Show all remote sessions
                        _availableSessions.value = sessions
                        Log.d(TAG, "SUCCESS: Loaded ${sessions.size} remote sessions")

                        // Reset download progress
                        val progressMap = sessions.associate { it.sessionId to DownloadState.Idle }
                        _downloadProgress.value = progressMap
                    },
                    onFailure = { error ->
                        Log.e(TAG, "FAILURE: Error loading sessions: ${error.message}", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "EXCEPTION: Error loading sessions: ${e.message}", e)
            } finally {
                _isLoading.value = false
                Log.d(TAG, "loadAvailableSessions() completed, isLoading=false")
            }
        }
    }

    fun toggleSessionSelection(sessionId: String) {
        val currentSelected = _selectedSessions.value.toMutableSet()
        if (currentSelected.contains(sessionId)) {
            currentSelected.remove(sessionId)
        } else {
            currentSelected.add(sessionId)
        }
        _selectedSessions.value = currentSelected
    }

    fun selectAll() {
        _selectedSessions.value = _availableSessions.value.map { it.sessionId }.toSet()
    }

    fun deselectAll() {
        _selectedSessions.value = emptySet()
    }

    fun checkSelectedSessions() {
        val selectedIds = _selectedSessions.value
        val sessionsToCheck = _availableSessions.value.filter { it.sessionId in selectedIds }

        if (sessionsToCheck.isEmpty()) {
            Log.w(TAG, "No sessions selected for check")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true

                sessionsToCheck.forEach { session ->
                    checkSessionStatus(session)
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun checkSessionStatus(session: GeoTrackerApiClient.RemoteSessionSummary) {
        Log.d(TAG, "Checking session: ${session.eventName} (ID: ${session.sessionId})")
        updateProgress(session.sessionId, DownloadState.Checking)

        // Check if session is already in local database
        val existingEvent = withContext(Dispatchers.IO) {
            database.eventDao().getEventBySessionId(session.sessionId)
        }

        if (existingEvent != null) {
            Log.d(TAG, "Session already downloaded: ${session.sessionId}")
            updateProgress(session.sessionId, DownloadState.AlreadyDownloaded)
        } else {
            Log.d(TAG, "Session ready for download: ${session.sessionId}")
            updateProgress(session.sessionId, DownloadState.ReadyToDownload)
        }
    }

    fun downloadSelectedSessions() {
        val selectedIds = _selectedSessions.value
        val sessionsToDownload = _availableSessions.value.filter { session ->
            session.sessionId in selectedIds &&
            _downloadProgress.value[session.sessionId] == DownloadState.ReadyToDownload
        }

        if (sessionsToDownload.isEmpty()) {
            Log.w(TAG, "No sessions ready to download (run check first or all already downloaded)")
            return
        }

        viewModelScope.launch {
            try {
                _isLoading.value = true

                sessionsToDownload.forEach { session ->
                    performDownload(session)
                }

                // Reload the list after downloads complete
                loadAvailableSessions()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun performDownload(session: GeoTrackerApiClient.RemoteSessionSummary) {
        updateProgress(session.sessionId, DownloadState.Downloading)

        val result = withContext(Dispatchers.IO) {
            apiClient.downloadSessionWithDetails(session.sessionId)
        }

        result.fold(
            onSuccess = { fullData ->
                try {
                    // Insert into local database
                    withContext(Dispatchers.IO) {
                        insertSessionToDatabase(fullData)
                    }
                    Log.d(TAG, "Download successful: ${session.eventName}")
                    updateProgress(session.sessionId, DownloadState.Success("Downloaded ${fullData.gpsPoints.size} points"))
                } catch (e: Exception) {
                    Log.e(TAG, "Error saving to database", e)
                    updateProgress(session.sessionId, DownloadState.Error("Save failed: ${e.message}"))
                }
            },
            onFailure = { error ->
                Log.e(TAG, "Download failed: ${error.message}")
                updateProgress(session.sessionId, DownloadState.Error(error.message ?: "Download failed"))
            }
        )
    }

    private suspend fun insertSessionToDatabase(data: GeoTrackerApiClient.FullSessionData) {
        // Get default user ID
        val sharedPreferences = getApplication<Application>()
            .getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
        val userId = sharedPreferences.getLong("userId", 1L)

        // Parse event date from startDateTime
        val eventDate = data.startDateTime?.let {
            try {
                it.substring(0, 10) // Extract YYYY-MM-DD
            } catch (e: Exception) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())
            }
        } ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())

        // Create Event
        val event = Event(
            userId = userId,
            eventName = data.eventName ?: "Imported Event",
            eventDate = eventDate,
            artOfSport = data.sportType ?: "Unknown",
            comment = data.comment ?: "",
            sessionId = data.sessionId,
            isUploaded = true,
            uploadedAt = System.currentTimeMillis(),
            startCity = data.startCity,
            startCountry = data.startCountry,
            startAddress = data.startAddress,
            endCity = data.endCity,
            endCountry = data.endCountry,
            endAddress = data.endAddress
        )

        val eventId = database.eventDao().insertEvent(event).toInt()
        Log.d(TAG, "Inserted event with ID: $eventId")

        // Create Locations
        val locations = data.gpsPoints.map { point ->
            Location(
                eventId = eventId,
                latitude = point.latitude,
                longitude = point.longitude,
                altitude = point.altitude ?: 0.0
            )
        }

        if (locations.isNotEmpty()) {
            database.locationDao().insertLocations(locations)
            Log.d(TAG, "Inserted ${locations.size} locations")
        }

        // Create Metrics
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val metrics = data.gpsPoints.mapIndexed { index, point ->
            val timeInMillis = point.receivedAt?.let {
                try {
                    isoFormat.parse(it.replace("Z", "").split("+")[0])?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis() + (index * 1000L)
                }
            } ?: (System.currentTimeMillis() + (index * 1000L))

            Metric(
                eventId = eventId,
                heartRate = point.heartRate ?: 0,
                heartRateDevice = "",
                speed = point.currentSpeed ?: 0f,
                distance = point.distance ?: 0.0,
                cadence = null,
                lap = point.lap,
                timeInMilliseconds = timeInMillis,
                unity = "km/h",
                elevation = point.altitude?.toFloat() ?: 0f,
                elevationGain = point.cumulativeElevationGain ?: 0f,
                elevationLoss = 0f,
                slope = point.slope ?: 0.0,
                steps = null,
                strideLength = null,
                temperature = point.temperature,
                accuracy = point.horizontalAccuracy,
                pressure = point.pressure,
                pressureAccuracy = point.pressureAccuracy,
                altitudeFromPressure = point.altitudeFromPressure,
                seaLevelPressure = point.seaLevelPressure
            )
        }

        if (metrics.isNotEmpty()) {
            database.metricDao().insertMetrics(metrics)
            Log.d(TAG, "Inserted ${metrics.size} metrics")
        }

        // Create LapTimes
        val lapTimes = data.lapTimes.map { lap ->
            LapTime(
                sessionId = data.sessionId,
                eventId = eventId,
                lapNumber = lap.lapNumber,
                startTime = lap.startTime,
                endTime = lap.endTime,
                distance = lap.distance
            )
        }

        if (lapTimes.isNotEmpty()) {
            database.lapTimeDao().insertLapTimes(lapTimes)
            Log.d(TAG, "Inserted ${lapTimes.size} lap times")
        }

        // Create Weather records from GPS points that have weather data
        val weatherRecords = data.gpsPoints.filter { point ->
            point.temperature != null || point.windSpeed != null || point.humidity != null
        }.map { point ->
            Weather(
                eventId = eventId,
                weatherRestApi = "remote_import",
                temperature = point.temperature ?: 0f,
                windSpeed = point.windSpeed ?: 0f,
                windDirection = point.windDirection?.toString() ?: "0",
                relativeHumidity = point.humidity ?: 0
            )
        }.distinctBy { "${it.temperature}_${it.windSpeed}_${it.windDirection}_${it.relativeHumidity}" }

        if (weatherRecords.isNotEmpty()) {
            weatherRecords.forEach { weather ->
                database.weatherDao().insertWeather(weather)
            }
            Log.d(TAG, "Inserted ${weatherRecords.size} weather records")
        }

        // Create DeviceStatus records from GPS points that have signal quality data
        // Use usedNumberOfSatellites (satellites used for fix) if available, otherwise numberOfSatellites
        val deviceStatusRecords = data.gpsPoints.filter { point ->
            point.numberOfSatellites != null || point.usedNumberOfSatellites != null ||
            point.horizontalAccuracy != null || point.verticalAccuracyMeters != null
        }.map { point ->
            // Store just the satellite count as a plain number string (matching local recording format)
            val satelliteCount = (point.usedNumberOfSatellites ?: point.numberOfSatellites ?: 0).toString()
            DeviceStatus(
                eventId = eventId,
                numberOfSatellites = satelliteCount,
                sensorAccuracy = point.horizontalAccuracy?.let { "%.2f m".format(it) } ?: "N/A",
                signalStrength = point.verticalAccuracyMeters?.let { "%.2f m".format(it) } ?: "N/A",
                batteryLevel = "N/A",
                connectionStatus = "imported",
                sessionId = data.sessionId
            )
        }.distinctBy { "${it.numberOfSatellites}_${it.sensorAccuracy}_${it.signalStrength}" }

        if (deviceStatusRecords.isNotEmpty()) {
            deviceStatusRecords.forEach { status ->
                database.deviceStatusDao().insertDeviceStatus(status)
            }
            Log.d(TAG, "Inserted ${deviceStatusRecords.size} device status records")
        }
    }

    private fun updateProgress(sessionId: String, state: DownloadState) {
        val currentProgress = _downloadProgress.value.toMutableMap()
        currentProgress[sessionId] = state
        _downloadProgress.value = currentProgress
    }

    fun clearDownloadState(sessionId: String) {
        updateProgress(sessionId, DownloadState.Idle)
    }
}
