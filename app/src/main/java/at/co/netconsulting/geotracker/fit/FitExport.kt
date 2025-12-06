package at.co.netconsulting.geotracker.fit

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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

suspend fun export(eventId: Int, contextActivity: Context) {
    val database = FitnessTrackerDatabase.getInstance(contextActivity)
    try {
        withContext(Dispatchers.IO) {
            val event = database.eventDao().getEventById(eventId)
            val locations = database.locationDao().getLocationsByEventId(eventId)
            val metrics = database.metricDao().getMetricsByEventId(eventId)
            val weather = database.weatherDao().getWeatherForEvent(eventId)

            if (locations.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(contextActivity, "No location data to export", Toast.LENGTH_SHORT).show()
                }
                return@withContext
            }

            // Map sport type to FIT sport and sub-sport
            val (sport, subSport) = mapSportType(event?.artOfSport)

            // Generate FIT file data
            val fitData = generateFitFile(
                eventName = event?.eventName ?: "Activity",
                eventDate = event?.eventDate ?: "",
                sport = sport,
                subSport = subSport,
                locations = locations,
                metrics = metrics,
                weather = weather.firstOrNull()
            )

            val filename = "${event?.eventName}_${event?.eventDate}.fit"
                .replace(" ", "_")
                .replace(":", "-")
                .replace("[^a-zA-Z0-9._-]".toRegex(), "_")

            // Save file using modern storage approach
            val success = saveFitFile(contextActivity, filename, fitData)

            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(
                        contextActivity,
                        "FIT file exported: $filename",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        contextActivity,
                        "Failed to export FIT file. Check storage permissions.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    } catch (e: Exception) {
        Log.e("FITExport", "Error exporting FIT", e)
        withContext(Dispatchers.Main) {
            Toast.makeText(
                contextActivity,
                "Error exporting FIT: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

/**
 * Map sport type to FIT sport and sub-sport codes
 */
private fun mapSportType(sportType: String?): Pair<Int, Int> {
    return when (sportType?.lowercase()) {
        "running", "jogging" -> Pair(1, 0) // Running, Generic
        "marathon", "trail running" -> Pair(1, 3) // Running, Trail
        "cycling", "bicycle", "bike", "biking" -> Pair(2, 0) // Cycling, Generic
        "mountain bike" -> Pair(2, 8) // Cycling, Mountain
        "gravel bike" -> Pair(2, 46) // Cycling, Gravel
        "e-bike" -> Pair(2, 21) // Cycling, E-Bike
        "racing bicycle" -> Pair(2, 10) // Cycling, Road
        "hiking", "walking" -> Pair(11, 0) // Hiking, Generic
        "swimming - open water" -> Pair(5, 1) // Swimming, Open Water
        "swimming - pool" -> Pair(5, 2) // Swimming, Lap Swimming
        "ski", "cross country skiing" -> Pair(12, 0) // Cross Country Skiing, Generic
        "snowboard" -> Pair(14, 0) // Snowboarding, Generic
        "training" -> Pair(10, 0) // Training, Generic
        else -> Pair(0, 0) // Generic, Generic
    }
}

/**
 * Generate FIT file binary data
 */
private fun generateFitFile(
    eventName: String,
    eventDate: String,
    sport: Int,
    subSport: Int,
    locations: List<at.co.netconsulting.geotracker.domain.Location>,
    metrics: List<at.co.netconsulting.geotracker.domain.Metric>,
    weather: at.co.netconsulting.geotracker.domain.Weather?
): ByteArray {
    val output = ByteArrayOutputStream()

    // Get timestamp for file creation
    val firstMetric = metrics.firstOrNull()
    val lastMetric = metrics.lastOrNull()
    val startTime = firstMetric?.timeInMilliseconds ?: System.currentTimeMillis()
    val endTime = lastMetric?.timeInMilliseconds ?: startTime

    // Convert to FIT timestamp (seconds since UTC 00:00 Dec 31 1989)
    val fitStartTime = toFitTimestamp(startTime)
    val fitEndTime = toFitTimestamp(endTime)

    // Calculate total distance, calories, etc.
    val totalDistance = metrics.lastOrNull()?.distance?.toFloat() ?: 0f
    val totalTimerTime = ((endTime - startTime) / 1000).toInt()
    val hrValues = metrics.filter { it.heartRate > 0 }.map { it.heartRate }
    val avgHeartRate = if (hrValues.isNotEmpty()) hrValues.average().toInt() else 0
    val maxHeartRate = metrics.maxOfOrNull { it.heartRate } ?: 0
    val speedValues = metrics.filter { it.speed > 0 }.map { it.speed }
    val avgSpeed = if (speedValues.isNotEmpty()) speedValues.average().toFloat() else 0f
    val maxSpeed = metrics.maxOfOrNull { it.speed }?.toFloat() ?: 0f
    val cadenceValues = metrics.mapNotNull { getMetricCadence(it) }.filter { it > 0 }
    val avgCadence = if (cadenceValues.isNotEmpty()) cadenceValues.average().toInt() else 0
    val maxCadence = cadenceValues.maxOrNull() ?: 0

    // Calculate total ascent and descent
    var totalAscent = 0f
    var totalDescent = 0f
    for (i in 1 until locations.size) {
        val altDiff = locations[i].altitude - locations[i - 1].altitude
        if (altDiff > 0) totalAscent += altDiff.toFloat()
        else totalDescent += Math.abs(altDiff.toFloat())
    }

    // Write FIT file header (12 bytes minimum, 14 bytes with CRC)
    val headerSize = 14
    output.write(headerSize) // Header size
    output.write(0x10) // Protocol version 1.0
    output.write(byteArrayOf(0x08, 0x08)) // Profile version 8.8 (little endian)

    // Data size placeholder (will be updated later)
    val dataSizePos = output.size()
    output.write(ByteArray(4)) // Placeholder for data size

    output.write(byteArrayOf('.'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte())) // .FIT signature

    // Header CRC (optional but recommended)
    val headerCrc = calculateCrc(output.toByteArray(), 0, 12)
    output.write(shortToBytes(headerCrc.toInt()))

    val dataStartPos = output.size()

    // Write File ID message (required, must be first)
    writeFileIdMessage(output, fitStartTime)

    // Write Record messages (individual trackpoints) - must come before Lap and Session
    writeRecordMessages(output, locations, metrics, weather, fitStartTime)

    // Write Lap messages - must come before Session
    writeLapMessages(output, locations, metrics, fitStartTime)

    // Write Session message (summary of the activity) - must come before Activity
    writeSessionMessage(
        output = output,
        startTime = fitStartTime,
        totalElapsedTime = totalTimerTime,
        totalTimerTime = totalTimerTime,
        totalDistance = totalDistance,
        sport = sport,
        subSport = subSport,
        avgHeartRate = avgHeartRate,
        maxHeartRate = maxHeartRate,
        avgSpeed = avgSpeed,
        maxSpeed = maxSpeed,
        avgCadence = avgCadence,
        maxCadence = maxCadence,
        totalAscent = totalAscent.toInt(),
        totalDescent = totalDescent.toInt(),
        startPosition = locations.firstOrNull()
    )

    // Write Activity message - must be last
    writeActivityMessage(output, fitStartTime, fitEndTime, totalTimerTime, sport)

    // Calculate data size and create final byte array
    val dataSize = output.size() - dataStartPos
    var finalBytes = output.toByteArray()

    // Update data size in header
    val dataSizeArray = intToBytes(dataSize)
    System.arraycopy(dataSizeArray, 0, finalBytes, dataSizePos, 4)

    // Calculate and append file CRC (CRC covers header + data, but not the CRC itself)
    val fileCrc = calculateCrc(finalBytes, 0, finalBytes.size)
    val crcBytes = shortToBytes(fileCrc.toInt())

    // Create final array with CRC appended
    val result = ByteArray(finalBytes.size + crcBytes.size)
    System.arraycopy(finalBytes, 0, result, 0, finalBytes.size)
    System.arraycopy(crcBytes, 0, result, finalBytes.size, crcBytes.size)

    return result
}

/**
 * Write File ID message (Message Type 0)
 */
private fun writeFileIdMessage(output: ByteArrayOutputStream, timestamp: Long) {
    // Definition message for File ID
    output.write(0x40) // Definition message, local message type 0
    output.write(0x00) // Reserved
    output.write(0x00) // Architecture (little endian)
    output.write(byteArrayOf(0x00, 0x00)) // Global message number 0 (File ID)
    output.write(0x05) // Number of fields

    // Field definitions
    // Field 0: Type (enum)
    output.write(byteArrayOf(0x00, 0x01, 0x00)) // Field def num 0, size 1, base type 0 (enum)
    // Field 1: Manufacturer (uint16)
    output.write(byteArrayOf(0x01, 0x02, 0x84.toByte())) // Field def num 1, size 2, base type 132 (uint16)
    // Field 2: Product (uint16)
    output.write(byteArrayOf(0x02, 0x02, 0x84.toByte())) // Field def num 2, size 2, base type 132 (uint16)
    // Field 3: Serial Number (uint32z)
    output.write(byteArrayOf(0x03, 0x04, 0x8C.toByte())) // Field def num 3, size 4, base type 140 (uint32z)
    // Field 4: Time Created (uint32)
    output.write(byteArrayOf(0x04, 0x04, 0x86.toByte())) // Field def num 4, size 4, base type 134 (uint32)

    // Data message for File ID
    output.write(0x00) // Data message, local message type 0
    output.write(0x04) // Type: Activity
    output.write(shortToBytes(255)) // Manufacturer: Development (255)
    output.write(shortToBytes(0)) // Product: 0
    output.write(intToBytes(12345)) // Serial number
    output.write(intToBytes(timestamp.toInt())) // Time created
}

/**
 * Write Activity message (Message Type 34)
 */
private fun writeActivityMessage(
    output: ByteArrayOutputStream,
    startTime: Long,
    endTime: Long,
    totalTimerTime: Int,
    sport: Int
) {
    // Definition message for Activity
    output.write(0x41) // Definition message, local message type 1
    output.write(0x00) // Reserved
    output.write(0x00) // Architecture (little endian)
    output.write(byteArrayOf(0x22, 0x00)) // Global message number 34 (Activity)
    output.write(0x04) // Number of fields

    // Field definitions
    output.write(byteArrayOf(0xFD.toByte(), 0x04, 0x86.toByte())) // Timestamp (uint32)
    output.write(byteArrayOf(0x00, 0x04, 0x86.toByte())) // Total timer time (uint32)
    output.write(byteArrayOf(0x01, 0x02, 0x84.toByte())) // Num sessions (uint16)
    output.write(byteArrayOf(0x02, 0x01, 0x00)) // Type (enum)

    // Data message for Activity
    output.write(0x01) // Data message, local message type 1
    output.write(intToBytes(endTime.toInt())) // Timestamp
    output.write(intToBytes(totalTimerTime * 1000)) // Total timer time in milliseconds
    output.write(shortToBytes(1)) // Number of sessions
    output.write(0x00) // Type: Manual
}

/**
 * Write Session message (Message Type 18)
 */
private fun writeSessionMessage(
    output: ByteArrayOutputStream,
    startTime: Long,
    totalElapsedTime: Int,
    totalTimerTime: Int,
    totalDistance: Float,
    sport: Int,
    subSport: Int,
    avgHeartRate: Int,
    maxHeartRate: Int,
    avgSpeed: Float,
    maxSpeed: Float,
    avgCadence: Int,
    maxCadence: Int,
    totalAscent: Int,
    totalDescent: Int,
    startPosition: at.co.netconsulting.geotracker.domain.Location?
) {
    // Definition message for Session
    output.write(0x42) // Definition message, local message type 2
    output.write(0x00) // Reserved
    output.write(0x00) // Architecture (little endian)
    output.write(byteArrayOf(0x12, 0x00)) // Global message number 18 (Session)
    output.write(0x11) // Number of fields (17 fields)

    // Field definitions
    output.write(byteArrayOf(0xFD.toByte(), 0x04, 0x86.toByte())) // Timestamp
    output.write(byteArrayOf(0x02, 0x04, 0x86.toByte())) // Start time
    output.write(byteArrayOf(0x07, 0x04, 0x86.toByte())) // Total elapsed time (ms)
    output.write(byteArrayOf(0x08, 0x04, 0x86.toByte())) // Total timer time (ms)
    output.write(byteArrayOf(0x09, 0x04, 0x86.toByte())) // Total distance (cm)
    output.write(byteArrayOf(0x05, 0x01, 0x00)) // Sport
    output.write(byteArrayOf(0x06, 0x01, 0x00)) // Sub sport
    output.write(byteArrayOf(0x0E, 0x02, 0x84.toByte())) // Avg heart rate
    output.write(byteArrayOf(0x0F, 0x02, 0x84.toByte())) // Max heart rate
    output.write(byteArrayOf(0x0A, 0x01, 0x02)) // Avg cadence
    output.write(byteArrayOf(0x0B, 0x01, 0x02)) // Max cadence
    output.write(byteArrayOf(0x0C, 0x02, 0x84.toByte())) // Avg speed (mm/s)
    output.write(byteArrayOf(0x0D, 0x02, 0x84.toByte())) // Max speed (mm/s)
    output.write(byteArrayOf(0x16, 0x02, 0x84.toByte())) // Total ascent (m)
    output.write(byteArrayOf(0x17, 0x02, 0x84.toByte())) // Total descent (m)
    output.write(byteArrayOf(0x03, 0x04, 0x85.toByte())) // Start position lat (semicircles)
    output.write(byteArrayOf(0x04, 0x04, 0x85.toByte())) // Start position long (semicircles)

    // Data message for Session
    output.write(0x02) // Data message, local message type 2
    output.write(intToBytes((startTime + totalTimerTime).toInt())) // Timestamp
    output.write(intToBytes(startTime.toInt())) // Start time
    output.write(intToBytes(totalElapsedTime * 1000)) // Total elapsed time in ms
    output.write(intToBytes(totalTimerTime * 1000)) // Total timer time in ms
    output.write(intToBytes((totalDistance * 100).toInt())) // Total distance in cm
    output.write(sport) // Sport
    output.write(subSport) // Sub sport
    output.write(shortToBytes(if (avgHeartRate > 0) avgHeartRate else 0xFFFF)) // Avg HR
    output.write(shortToBytes(if (maxHeartRate > 0) maxHeartRate else 0xFFFF)) // Max HR
    output.write(if (avgCadence > 0) avgCadence else 0xFF) // Avg cadence
    output.write(if (maxCadence > 0) maxCadence else 0xFF) // Max cadence
    output.write(shortToBytes((avgSpeed * 1000).toInt())) // Avg speed in mm/s
    output.write(shortToBytes((maxSpeed * 1000).toInt())) // Max speed in mm/s
    output.write(shortToBytes(totalAscent)) // Total ascent
    output.write(shortToBytes(totalDescent)) // Total descent

    // Start position
    if (startPosition != null) {
        output.write(intToBytes(degToSemicircles(startPosition.latitude))) // Start lat
        output.write(intToBytes(degToSemicircles(startPosition.longitude))) // Start long
    } else {
        output.write(intToBytes(0x7FFFFFFF)) // Invalid lat
        output.write(intToBytes(0x7FFFFFFF)) // Invalid long
    }
}

/**
 * Write Lap messages (Message Type 19)
 */
private fun writeLapMessages(
    output: ByteArrayOutputStream,
    locations: List<at.co.netconsulting.geotracker.domain.Location>,
    metrics: List<at.co.netconsulting.geotracker.domain.Metric>,
    startTime: Long
) {
    // Group by lap number
    val laps = metrics.groupBy { it.lap }

    if (laps.isEmpty()) return

    // Definition message for Lap
    output.write(0x43) // Definition message, local message type 3
    output.write(0x00) // Reserved
    output.write(0x00) // Architecture (little endian)
    output.write(byteArrayOf(0x13, 0x00)) // Global message number 19 (Lap)
    output.write(0x06) // Number of fields

    // Field definitions
    output.write(byteArrayOf(0xFD.toByte(), 0x04, 0x86.toByte())) // Timestamp
    output.write(byteArrayOf(0x02, 0x04, 0x86.toByte())) // Start time
    output.write(byteArrayOf(0x07, 0x04, 0x86.toByte())) // Total elapsed time
    output.write(byteArrayOf(0x09, 0x04, 0x86.toByte())) // Total distance
    output.write(byteArrayOf(0x0E, 0x02, 0x84.toByte())) // Avg heart rate
    output.write(byteArrayOf(0x0F, 0x02, 0x84.toByte())) // Max heart rate

    laps.forEach { (lapNum, lapMetrics) ->
        val firstMetric = lapMetrics.firstOrNull() ?: return@forEach
        val lastMetric = lapMetrics.lastOrNull() ?: return@forEach

        val lapStartTime = toFitTimestamp(firstMetric.timeInMilliseconds)
        val lapEndTime = toFitTimestamp(lastMetric.timeInMilliseconds)
        val lapDuration = ((lastMetric.timeInMilliseconds - firstMetric.timeInMilliseconds) / 1000).toInt()
        val lapDistance = lastMetric.distance - firstMetric.distance
        val hrValues = lapMetrics.filter { it.heartRate > 0 }.map { it.heartRate }
        val lapAvgHr = if (hrValues.isNotEmpty()) hrValues.average().toInt() else 0
        val lapMaxHr = lapMetrics.maxOfOrNull { it.heartRate } ?: 0

        // Data message for Lap
        output.write(0x03) // Data message, local message type 3
        output.write(intToBytes(lapEndTime.toInt())) // Timestamp
        output.write(intToBytes(lapStartTime.toInt())) // Start time
        output.write(intToBytes(lapDuration * 1000)) // Total elapsed time in ms
        output.write(intToBytes((lapDistance * 100).toInt())) // Total distance in cm
        output.write(shortToBytes(if (lapAvgHr > 0) lapAvgHr else 0xFFFF)) // Avg HR
        output.write(shortToBytes(if (lapMaxHr > 0) lapMaxHr else 0xFFFF)) // Max HR
    }
}

/**
 * Write Record messages (Message Type 20) - individual trackpoints
 */
private fun writeRecordMessages(
    output: ByteArrayOutputStream,
    locations: List<at.co.netconsulting.geotracker.domain.Location>,
    metrics: List<at.co.netconsulting.geotracker.domain.Metric>,
    weather: at.co.netconsulting.geotracker.domain.Weather?,
    startTime: Long
) {
    // Definition message for Record
    output.write(0x44) // Definition message, local message type 4
    output.write(0x00) // Reserved
    output.write(0x00) // Architecture (little endian)
    output.write(byteArrayOf(0x14, 0x00)) // Global message number 20 (Record)
    output.write(0x0A) // Number of fields (10 fields)

    // Field definitions
    output.write(byteArrayOf(0xFD.toByte(), 0x04, 0x86.toByte())) // Timestamp (uint32)
    output.write(byteArrayOf(0x00, 0x04, 0x85.toByte())) // Position lat (sint32)
    output.write(byteArrayOf(0x01, 0x04, 0x85.toByte())) // Position long (sint32)
    output.write(byteArrayOf(0x05, 0x04, 0x86.toByte())) // Distance (uint32, in cm)
    output.write(byteArrayOf(0x02, 0x02, 0x84.toByte())) // Altitude (uint16, in m * 5 + 500)
    output.write(byteArrayOf(0x06, 0x01, 0x02)) // Heart rate (uint8)
    output.write(byteArrayOf(0x04, 0x01, 0x02)) // Cadence (uint8)
    output.write(byteArrayOf(0x73, 0x02, 0x84.toByte())) // Speed (uint16, in mm/s)
    output.write(byteArrayOf(0x0D, 0x01, 0x01)) // Temperature (sint8)
    output.write(byteArrayOf(0x09, 0x01, 0x00)) // Grade (enum, derived from slope)

    locations.forEachIndexed { index, location ->
        val metric = metrics.getOrNull(index)
        if (metric != null && metric.timeInMilliseconds > 0) {
            val timestamp = toFitTimestamp(metric.timeInMilliseconds)

            // Data message for Record
            output.write(0x04) // Data message, local message type 4
            output.write(intToBytes(timestamp.toInt())) // Timestamp
            output.write(intToBytes(degToSemicircles(location.latitude))) // Latitude
            output.write(intToBytes(degToSemicircles(location.longitude))) // Longitude
            output.write(intToBytes((metric.distance * 100).toInt())) // Distance in cm

            // Altitude: (meters * 5) + 500, scaled to fit in uint16
            val altitudeScaled = ((location.altitude * 5) + 500).toInt().coerceIn(0, 65535)
            output.write(shortToBytes(altitudeScaled))

            // Heart rate
            output.write(if (metric.heartRate > 0) metric.heartRate else 0xFF)

            // Cadence
            val cadence = getMetricCadence(metric)
            output.write(if (cadence != null && cadence > 0) cadence else 0xFF)

            // Speed in mm/s
            output.write(shortToBytes((metric.speed * 1000).toInt()))

            // Temperature
            val temp = weather?.temperature ?: getMetricTemperature(metric)
            output.write(if (temp != null) temp.toInt() else 0x7F)

            // Grade (placeholder)
            output.write(0xFF) // Invalid/unknown
        }
    }
}

/**
 * Convert timestamp from milliseconds since epoch to FIT timestamp
 * FIT timestamp is seconds since UTC 00:00 Dec 31 1989
 */
private fun toFitTimestamp(millisSinceEpoch: Long): Long {
    val fitEpoch = 631065600L // Seconds between Unix epoch (1970) and FIT epoch (1989-12-31)
    return (millisSinceEpoch / 1000) - fitEpoch
}

/**
 * Convert degrees to semicircles (FIT position format)
 * Semicircles = degrees * (2^31 / 180)
 */
private fun degToSemicircles(degrees: Double): Int {
    return (degrees * (2147483648.0 / 180.0)).toInt()
}

/**
 * Calculate CRC-16 for FIT files
 */
private fun calculateCrc(data: ByteArray, start: Int, length: Int): Short {
    val crcTable = shortArrayOf(
        0x0000.toShort(), 0xCC01.toShort(), 0xD801.toShort(), 0x1400.toShort(),
        0xF001.toShort(), 0x3C00.toShort(), 0x2800.toShort(), 0xE401.toShort(),
        0xA001.toShort(), 0x6C00.toShort(), 0x7800.toShort(), 0xB401.toShort(),
        0x5000.toShort(), 0x9C01.toShort(), 0x8801.toShort(), 0x4400.toShort()
    )

    var crc: Short = 0
    for (i in start until start + length) {
        val byte = data[i].toInt() and 0xFF

        // Compute CRC for lower nibble
        var tmp = crcTable[crc.toInt() and 0x0F]
        crc = ((crc.toInt() shr 4) and 0x0FFF).toShort()
        crc = (crc.toInt() xor tmp.toInt() xor crcTable[byte and 0x0F].toInt()).toShort()

        // Compute CRC for upper nibble
        tmp = crcTable[crc.toInt() and 0x0F]
        crc = ((crc.toInt() shr 4) and 0x0FFF).toShort()
        crc = (crc.toInt() xor tmp.toInt() xor crcTable[(byte shr 4) and 0x0F].toInt()).toShort()
    }

    return crc
}

/**
 * Convert short to byte array (little endian)
 */
private fun shortToBytes(value: Int): ByteArray {
    return byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte()
    )
}

/**
 * Convert int to byte array (little endian)
 */
private fun intToBytes(value: Int): ByteArray {
    return byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte()
    )
}

/**
 * Save FIT file using modern storage approaches
 */
fun saveFitFile(context: Context, filename: String, content: ByteArray): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveFitFileScoped(context, filename, content)
        } else {
            saveFitFileLegacy(context, filename, content)
        }
    } catch (e: Exception) {
        Log.e("FITExport", "Error saving FIT file", e)
        false
    }
}

/**
 * Save FIT file using scoped storage (Android 10+)
 */
private fun saveFitFileScoped(context: Context, filename: String, content: ByteArray): Boolean {
    return try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.ant.fit")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GeoTracker")
        }

        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        uri?.let { fileUri ->
            resolver.openOutputStream(fileUri)?.use { outputStream ->
                outputStream.write(content)
                outputStream.flush()
            }
            true
        } ?: false
    } catch (e: Exception) {
        Log.e("FITExport", "Error with scoped storage", e)
        false
    }
}

/**
 * Save FIT file using legacy approach (Android 9 and below)
 */
private fun saveFitFileLegacy(context: Context, filename: String, content: ByteArray): Boolean {
    return try {
        val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "GeoTracker")
        if (!appDir.exists()) {
            appDir.mkdirs()
        }

        val file = File(appDir, filename)
        FileOutputStream(file).use { output ->
            output.write(content)
        }

        Log.d("FITExport", "FIT saved to app directory: ${file.absolutePath}")
        true
    } catch (e: Exception) {
        Log.e("FITExport", "Error with legacy storage", e)
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
                output.write(content)
            }

            Log.d("FITExport", "FIT saved to public directory: ${file.absolutePath}")
            true
        } catch (e2: Exception) {
            Log.e("FITExport", "Both storage methods failed", e2)
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

private fun getMetricCadence(metric: at.co.netconsulting.geotracker.domain.Metric): Int? {
    return try {
        val field = metric::class.java.getDeclaredField("cadence")
        field.isAccessible = true
        field.get(metric) as? Int
    } catch (e: Exception) {
        null
    }
}
