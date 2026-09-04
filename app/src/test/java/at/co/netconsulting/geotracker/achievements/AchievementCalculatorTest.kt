package at.co.netconsulting.geotracker.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AchievementCalculatorTest {
    @Test
    fun `finds fastest distance segment with interpolated finish`() {
        val samples = listOf(
            sample(minutes = 0, distance = 0.0),
            sample(minutes = 5, distance = 800.0),
            sample(minutes = 10, distance = 2_000.0)
        )

        assertEquals(
            250_000L,
            AchievementCalculator.bestElapsedTimeMillis(samples, 1_000.0)
        )
    }

    @Test
    fun `finds fastest distance segment after a pause`() {
        val samples = listOf(
            sample(minutes = 0, distance = 0.0),
            sample(minutes = 5, distance = 500.0),
            sample(minutes = 15, distance = 500.0),
            sample(minutes = 20, distance = 1_500.0)
        )

        assertEquals(
            300_000L,
            AchievementCalculator.bestElapsedTimeMillis(samples, 1_000.0)
        )
    }

    @Test
    fun `handles activities whose first stored sample has a distance offset`() {
        val samples = listOf(
            sample(minutes = 0, distance = 250.0),
            sample(minutes = 5, distance = 1_250.0)
        )

        assertEquals(
            300_000L,
            AchievementCalculator.bestElapsedTimeMillis(samples, 1_000.0)
        )
    }

    @Test
    fun `finds greatest distance in an exact duration window`() {
        val samples = (0..9).map { hour ->
            val distance = when {
                hour <= 3 -> hour * (10_000.0 / 3.0)
                hour <= 6 -> 10_000.0 + (hour - 3) * 10_000.0
                else -> 40_000.0 + (hour - 6) * (5_000.0 / 3.0)
            }
            sample(hours = hour, distance = distance)
        }

        assertEquals(
            40_000.0,
            AchievementCalculator.bestDistanceMeters(samples, 6L * HOUR)!!,
            0.001
        )
    }

    @Test
    fun `does not award a duration longer than the activity`() {
        val samples = listOf(
            sample(hours = 0, distance = 0.0),
            sample(hours = 5, distance = 30_000.0)
        )

        assertNull(AchievementCalculator.bestDistanceMeters(samples, 6L * HOUR))
    }

    @Test
    fun `groups legacy running race types into the Running family`() {
        val slowerRun = activity("Running", "Slow run", 1, 10, 1_000.0)
        val fasterRun = activity("Halfmarathon", "Fast run", 2, 5, 1_000.0)
        val ride = activity("Cycling", "Ride", 3, 2, 1_000.0)

        val results = AchievementCalculator.calculate(listOf(slowerRun, fasterRun, ride))

        assertEquals(listOf("Running", "Cycling"), results.map { it.sport })
        assertEquals(2, results.first().activityCount)
        assertEquals("Fast run", results.first().records.getValue(AchievementCalculator.distanceDefinitions.first()).eventName)
        assertSame(
            AchievementCalculator.distanceDefinitions.first(),
            results.first().records.keys.first()
        )
    }

    @Test
    fun `contains the complete requested milestone set`() {
        assertEquals(
            listOf("1 km", "5 km", "10 km", "Half marathon", "Marathon", "50 km", "100 km"),
            AchievementCalculator.distanceDefinitions.map { it.label }
        )
        assertEquals(
            listOf("6 h", "12 h", "24 h", "48 h", "72 h", "6 days"),
            AchievementCalculator.durationDefinitions.map { it.label }
        )
    }

    @Test
    fun `uses official half marathon and marathon distances`() {
        assertEquals(21_097.5, AchievementCalculator.distanceDefinitions.first { it.label == "Half marathon" }.target)
        assertEquals(42_195.0, AchievementCalculator.distanceDefinitions.first { it.label == "Marathon" }.target)
    }

    @Test
    fun `does not bridge a corrupt multi-day timestamp gap`() {
        val samples = listOf(
            sample(hours = 0, distance = 0.0),
            sample(hours = 1, distance = 10_000.0),
            sample(hours = 2, distance = 21_097.5),
            sample(hours = 240, distance = 21_097.5)
        )

        assertNull(AchievementCalculator.bestDistanceMeters(samples, 24L * HOUR))
    }

    @Test
    fun `awards a timed record when samples continuously cover the duration`() {
        val samples = (0..24).map { hour ->
            sample(hours = hour, distance = hour * 2_000.0)
        }

        assertEquals(
            48_000.0,
            AchievementCalculator.bestDistanceMeters(samples, 24L * HOUR)!!,
            0.001
        )
    }

    @Test
    fun `derives milestones from samples instead of the selected event format`() {
        val activity = AchievementActivity(
            eventId = 7,
            eventName = "Unclassified five kilometre run",
            eventDate = "2026-09-04",
            sport = "Running",
            sportFamily = "Running",
            eventFormat = "1 km",
            samples = listOf(
                sample(minutes = 0, distance = 0.0),
                sample(minutes = 25, distance = 5_000.0)
            )
        )

        val result = AchievementCalculator.calculate(listOf(activity)).single()
        val fiveKilometres = result.distanceDefinitions.first { it.label == "5 km" }

        assertEquals(activity.eventName, result.records.getValue(fiveKilometres).eventName)
    }

    private fun activity(
        sport: String,
        name: String,
        id: Int,
        minutes: Int,
        distance: Double
    ) = AchievementActivity(
        eventId = id,
        eventName = name,
        eventDate = "2026-09-02",
        sport = sport,
        samples = listOf(
            sample(minutes = 0, distance = 0.0),
            sample(minutes = minutes, distance = distance)
        )
    )

    private fun sample(
        hours: Int = 0,
        minutes: Int = 0,
        distance: Double
    ) = AchievementSample(
        timeMillis = hours * HOUR + minutes * 60_000L,
        distanceMeters = distance
    )

    private companion object {
        const val HOUR = 60L * 60 * 1_000
    }
}
