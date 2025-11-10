package at.co.netconsulting.geotracker.data

import org.osmdroid.util.GeoPoint

data class ImportedGpxTrack(
    val filename: String,
    val points: List<GeoPoint>,
    val timestamps: List<Long> = emptyList(), // Timestamps in milliseconds for each point (for ghost racer mode)
    val eventId: Int? = null // Original event ID if loaded from database
)