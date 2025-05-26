package at.co.netconsulting.geotracker.tools

import android.content.Context
import android.net.Uri
import android.util.Log
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import at.co.netconsulting.geotracker.domain.User
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Enhanced GPX Importer with better error handling and time parsing
 */
class GpxImporter(private val context: Context) {
    private val TAG = "GpxImporter"
    private val database = FitnessTrackerDatabase.getInstance(context)

    suspend fun importGpx(uri: Uri): Int {
        return try {
            Log.d(TAG, "Starting GPX import from URI: $uri")

            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream from URI")
                return -1
            }

            // Validate file content first
            val content = inputStream.readBytes()
            val contentString = String(content)

            if (!isValidGpxContent(contentString)) {
                Log.e(TAG, "Invalid GPX content detected")
                return -2
            }

            Log.d(TAG, "GPX content validation passed")

            // Parse using fresh input stream
            val result = parseGpxAndCreateEvent(content.inputStream())
            Log.d(TAG, "Import completed with result: $result")

            result
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied: ${e.message}")
            -3
        } catch (e: Exception) {
            Log.e(TAG, "Exception in importGpx: ${e.message}", e)
            -1
        }
    }

    private fun isValidGpxContent(content: String): Boolean {
        return content.contains("<?xml") &&
                content.contains("<gpx") &&
                content.contains("version=") &&
                (content.contains("<trk") || content.contains("<wpt"))
    }

    private suspend fun parseGpxAndCreateEvent(inputStream: InputStream): Int {
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(inputStream, null)

            var eventId = -1
            val trackPoints = mutableListOf<GpxTrackPoint>()
            var eventName = ""
            var sportType = ""
            var inTrackPoint = false
            var inTrack = false
            var inMetadata = false
            var currentPoint = GpxTrackPoint()

            Log.d(TAG, "Starting to parse GPX XML")
            var parserEventType = parser.eventType

            while (parserEventType != XmlPullParser.END_DOCUMENT) {
                when (parserEventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "metadata" -> {
                                inMetadata = true
                                Log.d(TAG, "Entered metadata section")
                            }
                            "trk" -> {
                                inTrack = true
                                Log.d(TAG, "Entered track section")
                            }
                            "name" -> {
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    val nameText = parser.text
                                    if (inTrack && eventName.isEmpty()) {
                                        eventName = nameText
                                        Log.d(TAG, "Found track name: $eventName")
                                    } else if (inMetadata && eventName.isEmpty()) {
                                        eventName = nameText
                                        Log.d(TAG, "Found metadata name: $eventName")
                                    }
                                }
                            }
                            "type" -> {
                                if (inTrack) {
                                    parser.next()
                                    if (parser.eventType == XmlPullParser.TEXT) {
                                        sportType = parser.text
                                        Log.d(TAG, "Found sport type: $sportType")
                                    }
                                }
                            }
                            "trkpt" -> {
                                inTrackPoint = true
                                currentPoint = GpxTrackPoint()
                                currentPoint.lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                                currentPoint.lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0

                                // Validate coordinates
                                if (currentPoint.lat == 0.0 && currentPoint.lon == 0.0) {
                                    Log.w(TAG, "Invalid coordinates found: lat=${currentPoint.lat}, lon=${currentPoint.lon}")
                                }
                            }
                            "ele" -> {
                                if (inTrackPoint) {
                                    parser.next()
                                    if (parser.eventType == XmlPullParser.TEXT) {
                                        currentPoint.ele = parser.text.toDoubleOrNull() ?: 0.0
                                    }
                                }
                            }
                            "time" -> {
                                if (inTrackPoint) {
                                    parser.next()
                                    if (parser.eventType == XmlPullParser.TEXT) {
                                        currentPoint.time = parseTime(parser.text)
                                        if (currentPoint.time == 0L) {
                                            Log.w(TAG, "Failed to parse time: ${parser.text}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "metadata" -> inMetadata = false
                            "trk" -> inTrack = false
                            "trkpt" -> {
                                if (inTrackPoint) {
                                    inTrackPoint = false
                                    // Only add valid track points
                                    if (currentPoint.lat != 0.0 || currentPoint.lon != 0.0) {
                                        trackPoints.add(currentPoint)
                                    }
                                }
                            }
                        }
                    }
                }
                parserEventType = parser.next()
            }

            Log.d(TAG, "Finished parsing GPX. Found ${trackPoints.size} track points")
            Log.d(TAG, "Event name: '$eventName', Sport type: '$sportType'")

            // Validate track points
            if (trackPoints.isEmpty()) {
                Log.e(TAG, "No valid track points found in GPX file")
                return 0
            }

            if (trackPoints.size < 2) {
                Log.e(TAG, "Insufficient track points for creating an event (need at least 2, found ${trackPoints.size})")
                return 0
            }

            // Remove duplicate consecutive points (common in GPX files)
            val filteredPoints = removeDuplicatePoints(trackPoints)
            Log.d(TAG, "After removing duplicates: ${filteredPoints.size} track points")

            eventId = createEventFromTrackPoints(filteredPoints, eventName, sportType)
            Log.d(TAG, "Created event with ID: $eventId")

            return eventId
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing GPX: ${e.message}", e)
            return -1
        } finally {
            try {
                inputStream.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing input stream: ${e.message}")
            }
        }
    }

    private fun removeDuplicatePoints(trackPoints: List<GpxTrackPoint>): List<GpxTrackPoint> {
        if (trackPoints.size <= 1) return trackPoints

        val filtered = mutableListOf<GpxTrackPoint>()
        filtered.add(trackPoints.first())

        for (i in 1 until trackPoints.size) {
            val current = trackPoints[i]
            val previous = filtered.last()

            // Keep point if coordinates are different or time difference is significant
            val distanceThreshold = 0.000001 // ~0.1m at equator
            val timeThreshold = 5000 // 5 seconds

            if (abs(current.lat - previous.lat) > distanceThreshold ||
                abs(current.lon - previous.lon) > distanceThreshold ||
                abs(current.time - previous.time) > timeThreshold) {
                filtered.add(current)
            }
        }

        return filtered
    }

    private fun parseTime(timeString: String): Long {
        return try {
            // Handle ISO 8601 timestamps with various formats
            val cleanTimeString = timeString.trim()
            Log.d(TAG, "Parsing time: $cleanTimeString")

            // Try modern Java time API first (handles milliseconds)
            try {
                val instant = Instant.parse(cleanTimeString)
                val result = instant.toEpochMilli()
                Log.d(TAG, "Successfully parsed time using Instant.parse: $result")
                return result
            } catch (e: Exception) {
                Log.d(TAG, "Instant.parse failed, trying SimpleDateFormat")
            }

            // Fallback to SimpleDateFormat with multiple patterns
            val patterns = arrayOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",    // With milliseconds and Z
                "yyyy-MM-dd'T'HH:mm:ss.SSSX",      // With milliseconds and timezone
                "yyyy-MM-dd'T'HH:mm:ss'Z'",        // Without milliseconds, with Z
                "yyyy-MM-dd'T'HH:mm:ssX",          // Without milliseconds, with timezone
                "yyyy-MM-dd'T'HH:mm:ss",           // Without timezone
            )

            for (pattern in patterns) {
                try {
                    val format = SimpleDateFormat(pattern, Locale.US)
                    val result = format.parse(cleanTimeString)?.time ?: 0L
                    if (result > 0) {
                        Log.d(TAG, "Successfully parsed time using pattern $pattern: $result")
                        return result
                    }
                } catch (e: Exception) {
                    // Continue to next pattern
                }
            }

            Log.w(TAG, "All time parsing attempts failed for: $cleanTimeString")
            System.currentTimeMillis()
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing time: $timeString", e)
            System.currentTimeMillis()
        }
    }

    private suspend fun createEventFromTrackPoints(
        trackPoints: List<GpxTrackPoint>,
        eventName: String,
        activityType: String
    ): Int {
        return try {
            Log.d(TAG, "Creating event from ${trackPoints.size} track points")

            // Ensure a default user exists
            val defaultUserId = ensureDefaultUserExists()
            if (defaultUserId == -1L) {
                Log.e(TAG, "Failed to create or find default user")
                return -1
            }
            Log.d(TAG, "Using user ID: $defaultUserId")

            // Generate event name if not provided
            val finalEventName = when {
                eventName.isNotBlank() -> eventName.trim()
                else -> "Imported GPX ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}"
            }

            // Validate event name length
            val truncatedEventName = if (finalEventName.length > 100) {
                finalEventName.substring(0, 97) + "..."
            } else {
                finalEventName
            }

            // Get the event date from the first track point time
            val eventDate = if (trackPoints.isNotEmpty() && trackPoints[0].time > 0) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(trackPoints[0].time))
            } else {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            }

            // Validate and normalize sport type
            val normalizedSportType = when (activityType.lowercase().trim()) {
                "running", "run" -> "Running"
                "cycling", "bike", "biking" -> "Cycling"
                "walking", "walk" -> "Walking"
                "hiking", "hike" -> "Hiking"
                "" -> "Unknown"
                else -> activityType.replaceFirstChar { it.uppercase() }
            }

            Log.d(TAG, "Creating event: name='$truncatedEventName', date='$eventDate', sport='$normalizedSportType', userId=$defaultUserId")

            // Create and insert the event
            val event = Event(
                userId = defaultUserId,
                eventName = truncatedEventName,
                eventDate = eventDate,
                artOfSport = normalizedSportType,
                comment = "Imported from GPX file"
            )

            val eventId = database.eventDao().insertEvent(event).toInt()
            Log.d(TAG, "Event created with ID: $eventId")

            if (eventId <= 0) {
                Log.e(TAG, "Failed to insert event into database")
                return -1
            }

            // Insert track points as Location and Metric entities
            insertTrackPointsAsEntities(eventId, trackPoints)
            Log.d(TAG, "Successfully inserted track points and metrics for event ID: $eventId")

            return eventId
        } catch (e: Exception) {
            Log.e(TAG, "Error creating event: ${e.message}", e)
            return -1
        }
    }

    private suspend fun ensureDefaultUserExists(): Long {
        return try {
            val userCount = database.userDao().getUserCount()
            Log.d(TAG, "Found $userCount users in database")

            if (userCount > 0) {
                val userId = database.userDao().getFirstUserId()
                Log.d(TAG, "Using existing user with ID: $userId")
                return userId
            }

            // Create default user
            val defaultUser = User(
                userId = 0,
                firstName = "Default",
                lastName = "User",
                birthDate = "2000-01-01",
                weight = 70.0f,
                height = 170.0f
            )

            val newUserId = database.userDao().insertUser(defaultUser)
            Log.d(TAG, "Created default user with ID: $newUserId")
            return newUserId
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring default user exists: ${e.message}", e)
            return -1L
        }
    }

    private suspend fun insertTrackPointsAsEntities(eventId: Int, trackPoints: List<GpxTrackPoint>) {
        try {
            Log.d(TAG, "Inserting ${trackPoints.size} track points for event $eventId")

            // Batch insert locations
            val locations = trackPoints.mapIndexed { index, point ->
                if (index % 1000 == 0) {
                    Log.d(TAG, "Processing location $index/${trackPoints.size}")
                }
                Location(
                    locationId = 0,
                    eventId = eventId,
                    latitude = point.lat,
                    longitude = point.lon,
                    altitude = point.ele
                )
            }

            database.locationDao().insertLocations(locations)
            Log.d(TAG, "Successfully inserted ${locations.size} location points")

            // Calculate and insert metrics
            if (trackPoints.size > 1) {
                val metrics = calculateMetrics(eventId, trackPoints)
                database.metricDao().insertMetrics(metrics)
                Log.d(TAG, "Successfully inserted ${metrics.size} metric points")
            } else {
                Log.w(TAG, "Not enough track points to calculate metrics")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting track points: ${e.message}", e)
            throw e
        }
    }

    private fun calculateMetrics(eventId: Int, trackPoints: List<GpxTrackPoint>): List<Metric> {
        val metrics = mutableListOf<Metric>()
        var totalDistance = 0.0

        try {
            Log.d(TAG, "Calculating metrics for ${trackPoints.size} track points")

            for (i in 1 until trackPoints.size) {
                if (i % 1000 == 0) {
                    Log.d(TAG, "Processing metric $i/${trackPoints.size}")
                }

                val prevPoint = trackPoints[i-1]
                val currentPoint = trackPoints[i]

                // Calculate time difference in milliseconds
                val timeDiffMs = currentPoint.time - prevPoint.time
                if (timeDiffMs <= 0) {
                    Log.w(TAG, "Invalid time difference at point $i: $timeDiffMs ms, skipping")
                    continue
                }

                // Calculate distance between points
                val distance = calculateDistance(
                    prevPoint.lat, prevPoint.lon,
                    currentPoint.lat, currentPoint.lon
                )

                // Skip if distance is too large (likely GPS error)
                if (distance > 1000) { // More than 1km between consecutive points
                    Log.w(TAG, "Suspicious distance at point $i: ${distance}m, skipping")
                    continue
                }

                totalDistance += distance

                // Calculate speed in m/s
                val speed = if (timeDiffMs > 0) {
                    distance / (timeDiffMs / 1000.0)
                } else {
                    0.0
                }

                // Cap unrealistic speeds (>100 km/h = 27.8 m/s)
                val cappedSpeed = if (speed > 27.8) {
                    Log.w(TAG, "Capping unrealistic speed at point $i: ${speed} m/s -> 27.8 m/s")
                    27.8
                } else {
                    speed
                }

                // Calculate elevation metrics
                val elevationDiff = currentPoint.ele - prevPoint.ele
                val elevationGain = if (elevationDiff > 0) elevationDiff else 0.0
                val elevationLoss = if (elevationDiff < 0) abs(elevationDiff) else 0.0

                val metric = Metric(
                    metricId = 0,
                    eventId = eventId,
                    timeInMilliseconds = currentPoint.time,
                    distance = totalDistance,
                    speed = cappedSpeed.toFloat(),
                    heartRate = 0,
                    heartRateDevice = "None",
                    cadence = null,
                    lap = calculateLapNumber(totalDistance),
                    unity = "metric",
                    elevation = currentPoint.ele.toFloat(),
                    elevationGain = elevationGain.toFloat(),
                    elevationLoss = elevationLoss.toFloat()
                )

                metrics.add(metric)
            }

            Log.d(TAG, "Calculated ${metrics.size} metric entries, total distance: ${"%.2f".format(totalDistance/1000)} km")
            return metrics
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating metrics: ${e.message}", e)
            throw e
        }
    }

    private fun calculateLapNumber(distance: Double): Int {
        return (distance / 1000.0).toInt()
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371e3 // Earth radius in meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val a = sin(deltaPhi / 2).pow(2) +
                cos(phi1) * cos(phi2) *
                sin(deltaLambda / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }

    private data class GpxTrackPoint(
        var lat: Double = 0.0,
        var lon: Double = 0.0,
        var ele: Double = 0.0,
        var time: Long = 0
    )
}