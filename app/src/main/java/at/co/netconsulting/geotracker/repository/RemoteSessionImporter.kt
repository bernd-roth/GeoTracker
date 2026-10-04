package at.co.netconsulting.geotracker.repository

import android.content.SharedPreferences
import androidx.room.withTransaction
import at.co.netconsulting.geotracker.domain.User
import android.util.Log
import at.co.netconsulting.geotracker.domain.DeviceStatus
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.data.SportCatalog
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.LapTime
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.domain.Weather
import at.co.netconsulting.geotracker.sync.GeoTrackerApiClient
import java.text.SimpleDateFormat
import java.util.Locale


/** Saves a remote recording atomically, resolving its local owner before any child rows. */
class RemoteSessionImporter(
    private val database: FitnessTrackerDatabase,
    private val preferences: SharedPreferences
) {
    suspend fun importSession(data: GeoTrackerApiClient.FullSessionData): Int {
        val (eventId, userId) = database.withTransaction {
            val existing = database.eventDao().getEventBySessionId(data.sessionId)
            if (existing != null) return@withTransaction existing.eventId to existing.userId
            val userId = resolveUser()
            insertSession(data, userId) to userId
        }
        preferences.edit().putLong("userId", userId).apply()
        return eventId
    }

    private suspend fun resolveUser(): Long {
        val firstName = preferences.getString("firstname", "").orEmpty()
        val lastName = preferences.getString("lastname", "").orEmpty()
        val birthDate = preferences.getString("birthdate", "").orEmpty()
        require(firstName.isNotBlank()) { "Configure your profile before downloading recordings." }
        val dao = database.userDao()
        val saved = dao.getUserById(preferences.getLong("userId", -1L))
        if (saved != null && saved.firstName == firstName && saved.lastName == lastName && saved.birthDate == birthDate) {
            return saved.userId.toLong()
        }
        return dao.findByProfile(firstName, lastName, birthDate)?.userId?.toLong()
            ?: dao.insertUser(User(
                firstName = firstName, lastName = lastName, birthDate = birthDate,
                weight = preferences.getFloat("weight", 0f), height = preferences.getFloat("height", 0f),
                bmi = preferences.getFloat("bmi", 0f)
            ))
    }

    private suspend fun insertSession(data: GeoTrackerApiClient.FullSessionData, userId: Long): Int {
        // Parse event date from startDateTime
        val eventDate = data.startDateTime?.let {
            try {
                it.substring(0, 10) // Extract YYYY-MM-DD
            } catch (e: Exception) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())
            }
        } ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(System.currentTimeMillis())

        val metadata = SportCatalog.resolve(
            legacySport = data.sportType ?: "Unknown",
            family = data.sportFamily,
            discipline = data.discipline,
            eventFormat = data.eventFormat
        )

        // Create Event
        val event = Event(
            userId = userId,
            eventName = data.eventName ?: "Imported Event",
            eventDate = eventDate,
            artOfSport = data.sportType ?: metadata.legacySportType(),
            comment = data.comment ?: "",
            sessionId = data.sessionId,
            isUploaded = true,
            uploadedAt = System.currentTimeMillis(),
            startCity = data.startCity,
            startCountry = data.startCountry,
            startAddress = data.startAddress,
            endCity = data.endCity,
            endCountry = data.endCountry,
            endAddress = data.endAddress,
            sportFamily = metadata.family,
            discipline = metadata.discipline,
            eventFormat = metadata.eventFormat
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
        val metrics = data.gpsPoints.map { point ->
            // Zero means unknown. Download time must never become recording time.
            val timeInMillis = parseRecordingTimestamp(point.receivedAt) ?: 0L

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
        return eventId
    }

    companion object { private const val TAG = "RemoteSessionImporter" }
}
