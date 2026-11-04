package at.co.netconsulting.geotracker.sync

import at.co.netconsulting.geotracker.domain.Location

/** Keep original indices so omitting missing fixes does not shift the paired metrics. */
internal fun locationsForUpload(locations: List<Location>): List<IndexedValue<Location>> {
    val points = locations.withIndex().filter { (_, location) ->
        location.latitude != -999.0 && location.longitude != -999.0
    }
    require(points.isNotEmpty()) { "Event has no valid GPS fixes to upload (only missing GPS placeholders)." }
    points.forEach { (index, location) -> validateUploadCoordinates(location, index + 1) }
    return points
}

/** Fail before sending an event whose coordinates cannot represent a GPS position. */
internal fun validateUploadCoordinates(location: Location, pointNumber: Int) {
    require(location.latitude.isFinite() && location.latitude in -90.0..90.0) {
        "GPS point $pointNumber (location ID ${location.locationId}) has invalid latitude " +
            "${location.latitude}. Expected a value between -90 and 90. Repair this point before uploading."
    }
    require(location.longitude.isFinite() && location.longitude in -180.0..180.0) {
        "GPS point $pointNumber (location ID ${location.locationId}) has invalid longitude " +
            "${location.longitude}. Expected a value between -180 and 180. Repair this point before uploading."
    }
}
