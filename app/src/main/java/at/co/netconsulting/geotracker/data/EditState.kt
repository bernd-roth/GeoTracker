package at.co.netconsulting.geotracker.data

data class EditState(
    val isEditing: Boolean = false,
    val eventId: Int = -1,
    val currentEventName: String = "",
    val currentEventDate: String = ""
)