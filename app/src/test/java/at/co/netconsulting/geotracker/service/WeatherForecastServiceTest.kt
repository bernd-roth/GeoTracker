package at.co.netconsulting.geotracker.service

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZonedDateTime

class WeatherForecastServiceTest {
    @Test
    fun viennaEveningUsesItsCurrentHour() {
        val times = listOf("2026-11-24T16:00:00Z", "2026-11-24T17:00:00Z").map {
            Instant.parse(it).epochSecond
        }
        val target = ZonedDateTime.parse("2026-11-24T17:55:00+01:00[Europe/Vienna]")

        assertEquals(0, routeForecastHourIndex(times, target.toInstant()))
    }

    @Test
    fun missingHourDoesNotUseUnrelatedForecast() {
        val times = listOf(Instant.parse("2026-11-25T00:00:00Z").epochSecond)
        val target = Instant.parse("2026-11-24T22:30:00Z")

        assertEquals(-1, routeForecastHourIndex(times, target))
    }
}
