package at.co.netconsulting.geotracker.composables

import at.co.netconsulting.geotracker.data.EventWithTotalDistance
import kotlin.test.Test
import kotlin.test.assertEquals

class WeeklyActivitySelectionTest {
    @Test
    fun `selection includes the whole week and only the selected sport`() {
        val events = listOf(
            event(1, "2026-09-14"),
            event(2, "2026-09-20"),
            event(3, "2026-09-13"),
            event(4, "2026-09-21"),
            event(5, "2026-09-16", "Bicycle"),
            event(6, "2025-09-15")
        )
        assertEquals(listOf(2, 1), weeklyActivities(events, 2026 to 38, "Running").map { it.eventId })
    }

    @Test
    fun `ISO week stays together across calendar years`() {
        val events = listOf(event(1, "2020-12-28"), event(2, "2021-01-03"), event(3, "2021-01-04"))
        assertEquals(2020 to 53, activityWeek("2021-01-03"))
        assertEquals(2026 to 1, activityWeek("2025-12-29"))
        assertEquals(listOf(2, 1), weeklyActivities(events, 2020 to 53, "Running").map { it.eventId })
    }

    @Test
    fun `discipline selection includes only contributing multisport activities`() {
        val events = listOf(
            event(1, "2026-09-14", "Triathlon").copy(disciplineDistances = mapOf("Swim" to 500.0)),
            event(2, "2026-09-15", "Triathlon").copy(disciplineDistances = mapOf("Run" to 1000.0)),
            event(3, "2026-09-16", "Swimming")
        )
        assertEquals(listOf(1), weeklyActivities(events, 2026 to 38, "Triathlon", "Swim").map { it.eventId })
    }

    private fun event(id: Int, date: String, sport: String = "Running") =
        EventWithTotalDistance(id, "Activity $id", sport, date, 1000.0)
}
