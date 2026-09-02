package at.co.netconsulting.geotracker.data

import at.co.netconsulting.geotracker.domain.Event

data class SimpleEventState(
    val eventId: Int = 0,
    val eventName: String = "",
    val eventDate: String = "",
    val artOfSport: String = "",
    val sportFamily: String = "",
    val discipline: String = "",
    val eventFormat: String = "",
    val comment: String = "",
    val clothing: String = "",

    // Original event to detect changes
    val originalEvent: Event? = null
)
