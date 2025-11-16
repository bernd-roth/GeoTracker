package at.co.netconsulting.geotracker.gpx

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

suspend fun export(eventId: Int, contextActivity: Context) {
    val database = FitnessTrackerDatabase.getInstance(contextActivity)
    try {
        withContext(Dispatchers.IO) {
            val event = database.eventDao().getEventById(eventId)
            val locations = database.locationDao().getLocationsByEventId(eventId)
            val metrics = database.metricDao().getMetricsByEventId(eventId)
            val weather = database.weatherDao().getWeatherForEvent(eventId)
            val deviceStatus = database.deviceStatusDao().getLastDeviceStatusByEvent(eventId)

            if (locations.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(contextActivity, "No location data to export", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            // Map sport type to GPX activity type
            val activityType = when (event?.artOfSport?.lowercase()) {
                "training" -> "training"
                "running", "jogging", "marathon", "trail running", "ultramarathon", "road running", "orienteering" -> "run"
                "cycling", "bicycle", "bike", "biking", "gravel bike", "e-bike", "racing bicycle", "mountain bike" -> "bike"
                "hiking", "walking", "trekking", "mountain hiking", "forest hiking", "nordic walking", "urban walking" -> "hike"
                "swimming - open water", "swimming - pool", "kayaking", "canoeing", "stand up paddleboarding" -> "swim"
                // Winter sports
                "ski", "snowboard", "cross country skiing", "ski touring", "ice skating", "ice hockey", "biathlon", "sledding", "snowshoeing", "winter sport" -> "winter"
                // Other sports
                "soccer", "american football", "fistball", "squash", "tennis", "basketball", "volleyball", "baseball", "badminton", "table tennis" -> "sport"
                "car", "motorcycle", "motorsport" -> "drive"
                else -> event?.artOfSport?.lowercase()?.replace(" ", "_") ?: "unknown"
            }

            // Create GPX content with proper namespace declarations
            val gpxBuilder = StringBuilder()
            if (event != null) {
                gpxBuilder.append("""<?xml version="1.0" encoding="UTF-8"?>
                        |<gpx version="1.1" 
                        |    creator="GeoTracker"
                        |    xmlns="http://www.topografix.com/GPX/1/1"
                        |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                        |    xmlns:gpxtpx="http://www.garmin.com/xmlschemas/TrackPointExtension/v1"
                        |    xmlns:custom="http://geotracker.netconsulting.at/xmlschemas/CustomExtension/v1"
                        |    xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd
                        |                        http://www.garmin.com/xmlschemas/TrackPointExtension/v1 http://www.garmin.com/xmlschemas/TrackPointExtensionv1.xsd">
                        |  <metadata>
                        |    <name>${event.eventName}</name>
                        |    <time>${event.eventDate}T00:00:00Z</time>
                        |  </metadata>
                        |  <trk>
                        |    <name>${event.eventName}</name>
                        |    <type>${activityType}</type>
                        |    <trkseg>
                    """.trimMargin())
            }

            locations.forEachIndexed { index, location ->
                val metric = metrics.getOrNull(index)
                val weatherInfo = weather.firstOrNull()
                val deviceInfo = deviceStatus

                // Only create a trackpoint if there's a valid metric with a valid timestamp
                if (metric != null && metric.timeInMilliseconds > 0) {
                    val timestamp = Instant.ofEpochMilli(metric.timeInMilliseconds)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                    gpxBuilder.append("""
        |      <trkpt lat="${location.latitude}" lon="${location.longitude}">
        |        <ele>${location.altitude}</ele>
        |        <time>${timestamp}</time>
        |        <extensions>""")

                    // Check if we have any Garmin-standard extensions (hr, atemp, cadence)
                    val hasHeartRate = metric.heartRate > 0
                    val temperature = weatherInfo?.temperature ?: getMetricTemperature(metric)
                    val cadence = metric.cadence
                    val hasGarminExtensions = hasHeartRate || temperature != null || (cadence != null && cadence > 0)

                    // Add Garmin TrackPointExtension wrapper if we have standard Garmin data
                    if (hasGarminExtensions) {
                        gpxBuilder.append("""
        |          <gpxtpx:TrackPointExtension>""")

                        // Add heart rate
                        if (hasHeartRate) {
                            gpxBuilder.append("""
        |            <gpxtpx:hr>${metric.heartRate}</gpxtpx:hr>""")
                        }

                        // Add air temperature
                        temperature?.let { temp ->
                            gpxBuilder.append("""
        |            <gpxtpx:atemp>${temp}</gpxtpx:atemp>""")
                        }

                        // Add cadence (use Garmin standard format)
                        cadence?.let { cad ->
                            if (cad > 0) {
                                gpxBuilder.append("""
        |            <gpxtpx:cad>${cad}</gpxtpx:cad>""")
                            }
                        }

                        gpxBuilder.append("""
        |          </gpxtpx:TrackPointExtension>""")
                    }

                    // Add custom extensions (outside TrackPointExtension)
                    gpxBuilder.append("""
        |          <custom:distance>${metric.distance}</custom:distance>""")

                    // Add lap number
                    gpxBuilder.append("""
        |          <custom:lap>${metric.lap}</custom:lap>""")

                    // Add activity type
                    gpxBuilder.append("""
        |          <custom:type>${activityType}</custom:type>""")

                    // Add satellite count if available
                    deviceInfo?.let { device ->
                        if (device.numberOfSatellites.isNotEmpty()) {
                            gpxBuilder.append("""
        |          <custom:sat>${device.numberOfSatellites}</custom:sat>""")
                        }
                    }

                    // Add speed
                    if (metric.speed > 0) {
                        gpxBuilder.append("""
        |          <custom:speed>${metric.speed}</custom:speed>""")
                    }

                    // Add GPS accuracy if available
                    getMetricAccuracy(metric)?.let { accuracy ->
                        if (accuracy > 0) {
                            gpxBuilder.append("""
        |          <custom:accuracy>${accuracy}</custom:accuracy>""")
                        }
                    }

                    // Add steps if available (from phone step counter or calculated)
                    getMetricSteps(metric)?.let { steps ->
                        if (steps > 0) {
                            gpxBuilder.append("""
        |          <custom:steps>${steps}</custom:steps>""")
                        }
                    }

                    // Add stride length if available (calculated from speed/cadence)
                    getMetricStrideLength(metric)?.let { strideLength ->
                        if (strideLength > 0) {
                            gpxBuilder.append("""
        |          <custom:stride_length>${strideLength}</custom:stride_length>""")
                        }
                    }

                    // Add slope if available
                    if (metric.slope != 0.0) {
                        gpxBuilder.append("""
        |          <custom:slope>${metric.slope}</custom:slope>""")
                    }

                    gpxBuilder.append("""
        |        </extensions>
        |      </trkpt>
        """.trimMargin())
                }
            }

            gpxBuilder.append("""
                |    </trkseg>
                |  </trk>
                |</gpx>
            """.trimMargin())

            val filename = "${event?.eventName}_${event?.eventDate}.gpx"
                .replace(" ", "_")
                .replace(":", "-")
                .replace("[^a-zA-Z0-9._-]".toRegex(), "_") // Remove any other problematic characters

            // Save file using modern storage approach
            val success = saveGpxFile(contextActivity, filename, gpxBuilder.toString())

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(
                        contextActivity,
                        "GPX file exported: $filename",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        contextActivity,
                        "Failed to export GPX file. Check storage permissions.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    } catch (e: Exception) {
        Log.e("GPXExport", "Error exporting GPX", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(
                contextActivity,
                "Error exporting GPX: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

/**
 * Save GPX file using modern storage approaches
 * Works with both older Android versions and Android 10+ scoped storage
 */
fun saveGpxFile(context: Context, filename: String, content: String): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (API 29+) - Use scoped storage
            saveGpxFileScoped(context, filename, content)
        } else {
            // Android 9 and below - Use traditional file approach
            saveGpxFileLegacy(context, filename, content)
        }
    } catch (e: Exception) {
        Log.e("GPXExport", "Error saving GPX file", e)
        false
    }
}

/**
 * Save GPX file using scoped storage (Android 10+)
 * No permissions required!
 */
private fun saveGpxFileScoped(context: Context, filename: String, content: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GeoTracker")
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { fileUri ->
            resolver.openOutputStream(fileUri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
                outputStream.flush()
            }
            true
        } ?: false
    } catch (e: Exception) {
        Log.e("GPXExport", "Error with scoped storage", e)
        false
    }
}

/**
 * Save GPX file using legacy approach (Android 9 and below)
 * Requires WRITE_EXTERNAL_STORAGE permission
 */
private fun saveGpxFileLegacy(context: Context, filename: String, content: String): Boolean {
    return try {
        // Try app-specific directory first (no permissions needed)
        val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "GeoTracker")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val file = File(appDir, filename)
        FileOutputStream(file).use { output ->
            output.write(content.toByteArray())
        }

        Log.d("GPXExport", "GPX saved to app directory: ${file.absolutePath}")
        true
    } catch (e: Exception) {
        Log.e("GPXExport", "Error with legacy storage", e)
        // Fallback: try public Downloads (requires permission)
        try {
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "GeoTracker"
            )
            if (!publicDir.exists()) {
                publicDir.mkdirs()
            }

            val file = File(publicDir, filename)
            FileOutputStream(file).use { output ->
                output.write(content.toByteArray())
            }

            Log.d("GPXExport", "GPX saved to public directory: ${file.absolutePath}")
            true
        } catch (e2: Exception) {
            Log.e("GPXExport", "Both storage methods failed", e2)
            false
        }
    }
}

// Helper functions to safely access new fields using reflection
private fun getMetricTemperature(metric: at.co.netconsulting.geotracker.domain.Metric): Float? {
    return try {
        val field = metric::class.java.getDeclaredField("temperature")
        field.isAccessible = true
        field.get(metric) as? Float
    } catch (e: Exception) {
        null
    }
}

private fun getMetricAccuracy(metric: at.co.netconsulting.geotracker.domain.Metric): Float? {
    return try {
        val field = metric::class.java.getDeclaredField("accuracy")
        field.isAccessible = true
        field.get(metric) as? Float
    } catch (e: Exception) {
        null
    }
}

private fun getMetricSteps(metric: at.co.netconsulting.geotracker.domain.Metric): Int? {
    return try {
        val field = metric::class.java.getDeclaredField("steps")
        field.isAccessible = true
        field.get(metric) as? Int
    } catch (e: Exception) {
        null
    }
}

private fun getMetricStrideLength(metric: at.co.netconsulting.geotracker.domain.Metric): Float? {
    return try {
        val field = metric::class.java.getDeclaredField("strideLength")
        field.isAccessible = true
        field.get(metric) as? Float
    } catch (e: Exception) {
        null
    }
}