package at.co.netconsulting.geotracker.data

import org.osmdroid.util.GeoPoint

data class SavedLocationData(
    val points: List<GeoPoint>,
    val isRecording: Boolean
)