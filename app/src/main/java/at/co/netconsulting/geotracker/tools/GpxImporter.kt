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
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Class to handle GPX import functionality
 */
class GpxImporter(private val context: Context) {
    private val TAG = "GpxImporter" // For logging
    private val database = FitnessTrackerDatabase.getInstance(context)

    /**
     * Import a GPX file from a URI and create a new event
     * @param uri URI of the GPX file
     * @return ID of the newly created event, or -1 if import failed
     */
    suspend fun importGpx(uri: Uri): Int {
        try {
            Log.d(TAG, "Starting GPX import from URI: $uri")
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream from URI")
                return -1
            }
            return parseGpxAndCreateEvent(inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Exception in importGpx: ${e.message}", e)
            return -1
        }
    }

    private suspend fun parseGpxAndCreateEvent(inputStream: InputStream): Int {
        try {
            // Initialize XML parser
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(inputStream, null)

            var eventId = -1
            val trackPoints = mutableListOf<GpxTrackPoint>()
            var eventName = ""
            var sportType = ""
            var inTrackPoint = false
            var currentPoint = GpxTrackPoint()

            // Parse the XML
            Log.d(TAG, "Starting to parse GPX XML")
            var parserEventType = parser.eventType
            while (parserEventType != XmlPullParser.END_DOCUMENT) {
                when (parserEventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "name" -> {
                                // Parse event name
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    eventName = parser.text
                                    Log.d(TAG, "Found event name: $eventName")
                                }
                            }
                            "type" -> {
                                // Parse activity type
                                parser.next()
                                if (parser.eventType == XmlPullParser.TEXT) {
                                    sportType = parser.text
                                    Log.d(TAG, "Found sport type: $sportType")
                                }
                            }
                            "trkpt" -> {
                                inTrackPoint = true
                                currentPoint = GpxTrackPoint()
                                // Get latitude and longitude from attributes
                                currentPoint.lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                                currentPoint.lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
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
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "trkpt" && inTrackPoint) {
                            inTrackPoint = false
                            trackPoints.add(currentPoint)
                        }
                    }
                }
                parserEventType = parser.next()
            }

            Log.d(TAG, "Finished parsing GPX. Found ${trackPoints.size} track points")

            // If we have valid track points, create the event
            if (trackPoints.isNotEmpty()) {
                eventId = createEventFromTrackPoints(trackPoints, eventName, sportType)
                Log.d(TAG, "Created event with ID: $eventId")
            } else {
                Log.e(TAG, "No track points found in GPX file")
            }

            return eventId
        } catch (e: Exception) {
            Log.e(TAG, "Exception parsing GPX: ${e.message}", e)
            e.printStackTrace()
            return -1
        } finally {
            try {
                inputStream.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing input stream: ${e.message}")
            }
        }
    }

    private fun parseTime(timeString: String): Long {
        return try {
            // Parse ISO 8601 timestamp from GPX (e.g., "2023-04-19T14:30:22Z")
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            format.parse(timeString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            try {
                // Try alternative format without Z
                val altFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                altFormat.parse(timeString)?.time ?: System.currentTimeMillis()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse time: $timeString, using current time")
                System.currentTimeMillis()
            }
        }
    }

    private suspend fun createEventFromTrackPoints(
        trackPoints: List<GpxTrackPoint>,
        eventName: String,
        activityType: String
    ): Int {
        try {
            // Ensure a default user exists
            val defaultUserId = ensureDefaultUserExists()
            if (defaultUserId == -1L) {
                Log.e(TAG, "Failed to create or find default user")
                return -1
            }

            // Generate event name if not provided
            val finalEventName = if (eventName.isBlank()) {
                "Imported GPX ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}"
            } else {
                eventName
            }

            // Get the event date from the first track point time
            val eventDate = if (trackPoints.isNotEmpty() && trackPoints[0].time > 0) {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(trackPoints[0].time))
            } else {
                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            }

            // Sport type - use provided or default to "Unknown"
            val sportType = if (activityType.isBlank()) "Unknown" else activityType

            Log.d(TAG, "Creating event: name=$finalEventName, date=$eventDate, sport=$sportType, userId=$defaultUserId")

            // Create and insert the event
            val event = Event(
                userId = defaultUserId,
                eventName = finalEventName,
                eventDate = eventDate,
                artOfSport = sportType,
                comment = "Imported from GPX file"
            )

            val eventId = database.eventDao().insertEvent(event).toInt()
            Log.d(TAG, "Event created with ID: $eventId")

            // Insert track points as Location and Metric entities
            if (eventId > 0 && trackPoints.isNotEmpty()) {
                insertTrackPointsAsEntities(eventId, trackPoints)
                Log.d(TAG, "Inserted track points and metrics for event ID: $eventId")
            } else {
                Log.e(TAG, "Failed to insert event or track points")
            }

            return eventId
        } catch (e: Exception) {
            Log.e(TAG, "Error creating event: ${e.message}", e)
            return -1
        }
    }

    /**
     * Ensures that a default user exists in the database
     * @return The ID of the default user
     */
    private suspend fun ensureDefaultUserExists(): Long {
        try {
            // First, try to find existing users
            val userCount = database.userDao().getUserCount()

            if (userCount > 0) {
                // Get the first user ID
                val userId = database.userDao().getFirstUserId()
                Log.d(TAG, "Found existing user with ID: $userId")
                return userId
            }

            // If no users exist, create a default one
            val defaultUser = User(
                userId = 0, // Will be auto-generated
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
            // Insert each location point
            val locations = trackPoints.map { point ->
                Location(
                    locationId = 0, // Room will generate this
                    eventId = eventId,
                    latitude = point.lat,
                    longitude = point.lon,
                    altitude = point.ele
                )
            }

            Log.d(TAG, "Inserting ${locations.size} location points")
            database.locationDao().insertLocations(locations)

            // Calculate metrics (speed, distance) and insert them
            if (trackPoints.size > 1) {
                val metrics = calculateMetrics(eventId, trackPoints)
                Log.d(TAG, "Inserting ${metrics.size} metric points")
                database.metricDao().insertMetrics(metrics)
            } else {
                Log.w(TAG, "Not enough track points to calculate metrics")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting track points: ${e.message}", e)
            throw e // Re-throw to handle in parent function
        }
    }

    private fun calculateMetrics(eventId: Int, trackPoints: List<GpxTrackPoint>): List<Metric> {
        val metrics = mutableListOf<Metric>()
        var totalDistance = 0.0
        var maxSpeed = 0.0

        try {
            for (i in 1 until trackPoints.size) {
                val prevPoint = trackPoints[i-1]
                val currentPoint = trackPoints[i]

                // Calculate time difference in seconds
                val timeDiffMs = currentPoint.time - prevPoint.time
                if (timeDiffMs <= 0) {
                    Log.w(TAG, "Invalid time difference between points: $timeDiffMs ms, skipping point")
                    continue
                }

                // Calculate distance between points
                val distance = calculateDistance(
                    prevPoint.lat, prevPoint.lon,
                    currentPoint.lat, currentPoint.lon
                )

                totalDistance += distance

                // Calculate speed in m/s
                val speed = if (timeDiffMs > 0) {
                    distance / (timeDiffMs / 1000.0)
                } else {
                    0.0
                }

                maxSpeed = maxOf(maxSpeed, speed)

                // Calculate elevation metrics
                val elevationDiff = currentPoint.ele - prevPoint.ele
                val elevationGain = if (elevationDiff > 0) elevationDiff else 0.0
                val elevationLoss = if (elevationDiff < 0) abs(elevationDiff) else 0.0

                // Create Metric object
                val metric = Metric(
                    metricId = 0, // Room will generate this
                    eventId = eventId,
                    timeInMilliseconds = currentPoint.time,
                    distance = totalDistance,
                    speed = speed.toFloat(),
                    heartRate = 0, // Default value, not available in GPX
                    heartRateDevice = "None", // Default value, not available in GPX
                    cadence = null, // Default value, not available in GPX
                    lap = calculateLapNumber(totalDistance),
                    unity = "metric",
                    elevation = currentPoint.ele.toFloat(),
                    elevationGain = elevationGain.toFloat(),
                    elevationLoss = elevationLoss.toFloat()
                )

                metrics.add(metric)
            }

            Log.d(TAG, "Calculated ${metrics.size} metric entries, total distance: ${totalDistance/1000} km")
            return metrics
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating metrics: ${e.message}", e)
            throw e // Re-throw to handle in parent function
        }
    }

    private fun calculateLapNumber(distance: Double): Int {
        // Assuming 1 km per lap
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

        return r * c // Distance in meters
    }

    /**
     * Helper data class for GPX track points
     */
    private data class GpxTrackPoint(
        var lat: Double = 0.0,
        var lon: Double = 0.0,
        var ele: Double = 0.0,
        var time: Long = 0
    )
}