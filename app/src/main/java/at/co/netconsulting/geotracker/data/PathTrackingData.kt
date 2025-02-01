package at.co.netconsulting.geotracker.data

import org.osmdroid.util.GeoPoint

data class PathTrackingData(
    val points: List<GeoPoint>,
    val isRecording: Boolean,
    val startPoint: GeoPoint? = null,
    val timestamp: Long = System.currentTimeMillis()
)