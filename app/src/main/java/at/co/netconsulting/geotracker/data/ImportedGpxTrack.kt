package at.co.netconsulting.geotracker.data

import org.osmdroid.util.GeoPoint

data class ImportedGpxTrack(
    val filename: String,
    val points: List<GeoPoint>
)