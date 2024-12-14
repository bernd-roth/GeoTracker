package at.co.netconsulting.geotracker.gpx

import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.LocalTime

suspend fun export(eventId: Int, contextActivity: Context) {
    var database = FitnessTrackerDatabase.getInstance(contextActivity)
    try {
        withContext(Dispatchers.IO) {
            val event = database.eventDao().getEventById(eventId)
            val locations = database.locationDao().getLocationsByEventId(eventId)
            val metrics = database.metricDao().getMetricsByEventId(eventId)

            if (locations.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(contextActivity, "No location data to export", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }
            // Create GPX content
            val gpxBuilder = StringBuilder()
            gpxBuilder.append("""<?xml version="1.0" encoding="UTF-8"?>
                |<gpx version="1.1" 
                |    creator="GeoTracker"
                |    xmlns="http://www.topografix.com/GPX/1/1"
                |    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                |    xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">
                |  <metadata>
                |    <name>${event.eventName}</name>
                |    <time>${event.eventDate}T00:00:00Z</time>
                |  </metadata>
                |  <trk>
                |    <name>${event.eventName}</name>
                |    <trkseg>
            """.trimMargin())

            locations.forEachIndexed { index, location ->
                val metric = metrics.getOrNull(index)
                val timeInMillis = metric?.timeInMilliseconds ?: 0L

                // Convert the timestamp to Instant and format it
                val timestamp = Instant.ofEpochMilli(timeInMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                gpxBuilder.append("""
        |      <trkpt lat="${location.latitude}" lon="${location.longitude}">
        |        <ele>${location.altitude}</ele>
        |        <time>${timestamp}</time>
        |        ${if (metric?.speed != null) "<speed>${metric.speed}</speed>" else ""}
        |        ${if (metric?.heartRate != null && metric.heartRate > 0) "<hr>${metric.heartRate}</hr>" else ""}
        |        ${if (metric?.cadence != null) "<cad>${metric.cadence}</cad>" else ""}
        |      </trkpt>
    """.trimMargin())
                Log.d("GpxExport", "TimeInMillis: $timeInMillis, Date: ${Instant.ofEpochMilli(timeInMillis)}")
            }

            gpxBuilder.append("""
                |    </trkseg>
                |  </trk>
                |</gpx>
            """.trimMargin())

            val filename = "GeoTracker_${event.eventName}_${event.eventDate}.gpx"
                .replace(" ", "_")
                .replace(":", "-")

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, filename)

            FileOutputStream(file).use { output ->
                output.write(gpxBuilder.toString().toByteArray())
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    contextActivity,
                    "GPX file exported to Downloads/$filename",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                contextActivity,
                "Error exporting GPX: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
        e.printStackTrace()
    }
}