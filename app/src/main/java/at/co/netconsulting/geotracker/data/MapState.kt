package at.co.netconsulting.geotracker.data

import org.osmdroid.util.GeoPoint

data class MapState(
    val points: List<GeoPoint>,
    val zoomLevel: Double?,
    val center: GeoPoint?,
    val isRecording: Boolean
)
