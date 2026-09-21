package at.co.netconsulting.geotracker.composables

import at.co.netconsulting.geotracker.data.EventWithTotalDistance
import java.time.LocalDate
import java.time.temporal.IsoFields

internal fun activityWeek(date: String): Pair<Int, Int> {
    val day = LocalDate.parse(date)
    return day.get(IsoFields.WEEK_BASED_YEAR) to day.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
}

internal fun weeklyActivities(
    events: List<EventWithTotalDistance>,
    yearWeek: Pair<Int, Int>,
    sport: String,
    discipline: String? = null
): List<EventWithTotalDistance> = events.filter {
    activityWeek(it.eventDate) == yearWeek && it.artOfSport == sport &&
        (discipline == null || discipline in it.disciplineDistances)
}.sortedWith(compareByDescending<EventWithTotalDistance> { it.eventDate }.thenByDescending { it.eventId })
