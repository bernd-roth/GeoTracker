package at.co.netconsulting.geotracker

import at.co.netconsulting.geotracker.domain.PlannedEvent
import at.co.netconsulting.geotracker.domain.expandCompetitionsByDate
import at.co.netconsulting.geotracker.domain.plannedEventDateError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannedEventDatesTest {
    @Test
    fun repeatsEveryDayInRangeForTheChosenNumberOfYears() {
        val event = event("2026-06-10", "2026-06-12", 3)
        for (year in 2026..2028) {
            assertEquals(
                mapOf("$year-06-10" to listOf(event), "$year-06-11" to listOf(event), "$year-06-12" to listOf(event)),
                expandCompetitionsByDate(listOf(event), year)
            )
        }
        assertTrue(expandCompetitionsByDate(listOf(event), 2025).isEmpty())
        assertTrue(expandCompetitionsByDate(listOf(event), 2029).isEmpty())
    }

    @Test
    fun includesTheJanuaryDaysAfterTheLastAnnualStart() {
        val event = event("2026-12-30", "2027-01-02", 2)
        assertEquals(
            setOf("2027-01-01", "2027-01-02", "2027-12-30", "2027-12-31"),
            expandCompetitionsByDate(listOf(event), 2027).keys
        )
        assertEquals(setOf("2028-01-01", "2028-01-02"), expandCompetitionsByDate(listOf(event), 2028).keys)
    }

    @Test
    fun clampsLeapDayAndRestoresItInTheNextLeapYear() {
        val event = event("2028-02-29", "", 5)
        assertEquals(setOf("2029-02-28"), expandCompetitionsByDate(listOf(event), 2029).keys)
        assertEquals(setOf("2032-02-29"), expandCompetitionsByDate(listOf(event), 2032).keys)
    }

    @Test
    fun overlappingOccurrencesShowOncePerDay() {
        val event = event("2026-06-10", "2027-06-12", 2)
        val days = expandCompetitionsByDate(listOf(event), 2027)
        assertEquals(365, days.size)
        assertEquals(listOf(event), days["2027-06-11"])
    }

    @Test
    fun validatesDatesAndTheNumberOfYears() {
        assertNull(plannedEventDateError("2026-06-10", "2026-06-12", true, 3))
        assertNotNull(plannedEventDateError("2026-06-10", "2026-06-09", true, 3))
        assertNotNull(plannedEventDateError("2026-06-10", "", true, 1))
        assertNotNull(plannedEventDateError("9998-12-30", "9999-01-02", true, 2))
    }

    private fun event(start: String, end: String, repeatYears: Int) = PlannedEvent(
        userId = 1,
        plannedEventName = "Race",
        plannedEventDate = start,
        plannedEventEndDate = end,
        plannedEventType = "",
        plannedEventCountry = "Austria",
        plannedEventCity = "Vienna",
        plannedLatitude = null,
        plannedLongitude = null,
        annualRepeatYears = repeatYears
    )
}
