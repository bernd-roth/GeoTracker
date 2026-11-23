package at.co.netconsulting.geotracker.domain

import java.time.LocalDate

internal fun plannedEventDateError(
    startDate: String,
    endDate: String,
    repeatAnnually: Boolean,
    years: Int?
): String? {
    val start = parsePlannedEventDate(startDate) ?: return "Select a valid start date."
    val end = if (endDate.isBlank()) start else parsePlannedEventDate(endDate)
    if (end == null || end.isBefore(start)) {
        return "Select an end date on or after the start date."
    }
    if (repeatAnnually && (years == null || years !in 2..(10000 - end.year))) {
        val maxYears = 10000 - end.year
        return if (maxYears >= 2) "Enter between 2 and $maxYears years, including the first year."
        else "Choose an earlier date range to repeat this event."
    }
    return null
}

internal fun expandCompetitionsByDate(
    competitions: List<PlannedEvent>,
    year: Int
): Map<String, List<PlannedEvent>> {
    val yearStart = LocalDate.of(year, 1, 1)
    val yearEnd = LocalDate.of(year, 12, 31)
    val result = mutableMapOf<String, MutableList<PlannedEvent>>()
    for (competition in competitions) {
        val start = parsePlannedEventDate(competition.plannedEventDate) ?: continue
        val end = parsePlannedEventDate(competition.plannedEventEndDate)
            ?.takeUnless { it.isBefore(start) } ?: start
        val lastOffset = competition.annualRepeatYears.coerceIn(1, 10000 - end.year).toLong() - 1
        var day = maxOf(yearStart, start)
        val lastDay = minOf(yearEnd, end.plusYears(lastOffset))
        while (!day.isAfter(lastDay)) {
            // The most recent occurrence also has the latest end date, even for overlapping ranges.
            var offset = minOf((day.year - start.year).toLong(), lastOffset)
            if (day.isBefore(start.plusYears(offset))) offset--
            if (offset >= 0 && !day.isAfter(end.plusYears(offset))) {
                result.getOrPut(day.toString()) { mutableListOf() }.add(competition)
            }
            day = day.plusDays(1)
        }
    }
    return result
}

private fun parsePlannedEventDate(date: String): LocalDate? =
    runCatching { LocalDate.parse(date) }.getOrNull()?.takeIf { it.year in 1..9999 }
