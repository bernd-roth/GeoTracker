package at.co.netconsulting.geotracker.location

import android.location.Location
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class MovementDistanceFilterTest {
    // Synthetic positions are spaced along a line, with one degree representing 1000 m.
    private fun filter() = MovementDistanceFilter { a, b ->
        abs(b.longitude - a.longitude) * 1000.0
    }

    private fun sample(
        millis: Long,
        metres: Double,
        speed: Float? = 1f,
        accuracy: Float? = 0.1f
    ) = MovementDistanceFilter.Sample(millis * 1_000_000L, 48.0, metres / 1000.0, speed, accuracy)

    @Test
    fun stationaryJitterDoesNotAccumulateDistance() {
        val filter = filter()
        repeat(3600) { second ->
            assertEquals(0.0, filter.update(sample(second * 1000L, (second % 17).toDouble(), 0.2f)), 0.0)
        }
        assertFalse(filter.isMoving)
    }

    @Test
    fun speedWhoseUncertaintyIncludesZeroDoesNotCount() {
        val filter = filter()
        repeat(10) { second ->
            assertEquals(0.0, filter.update(sample(second * 1000L, second * 10.0, 3f, 5f)), 0.0)
        }
        assertEquals(0.0, filter.update(sample(10_000, 100.0, 1f, 1f)), 0.0)
        assertFalse(filter.isMoving)
    }

    @Test
    fun isolatedSpeedSpikesDoNotAccumulate() {
        val filter = filter()
        repeat(60) { second ->
            val speed = if (second % 3 == 0) 2f else 0f
            assertEquals(0.0, filter.update(sample(second * 1000L, second * 10.0, speed)), 0.0)
        }
    }

    @Test
    fun confirmationReleasesBufferedDistanceExactlyOnce() {
        val filter = filter()
        assertEquals(0.0, filter.update(sample(0, 0.0)), 0.0)
        assertEquals(0.0, filter.update(sample(1000, 1.0)), 0.0)
        assertFalse(filter.isMoving)
        assertEquals(2.0, filter.update(sample(2000, 2.0)), 0.0001)
        assertTrue(filter.isMoving)
        assertEquals(1.0, filter.update(sample(3000, 3.0)), 0.0001)
    }

    @Test
    fun threeRapidSamplesStillNeedTwoSeconds() {
        val filter = filter()
        assertEquals(0.0, filter.update(sample(0, 0.0)), 0.0)
        assertEquals(0.0, filter.update(sample(100, 0.1)), 0.0)
        assertEquals(0.0, filter.update(sample(200, 0.2)), 0.0)
        assertEquals(2.0, filter.update(sample(2000, 2.0)), 0.0001)
    }

    @Test
    fun twoWidelySpacedSamplesStillNeedAThird() {
        val filter = filter()
        assertEquals(0.0, filter.update(sample(0, 0.0)), 0.0)
        assertEquals(0.0, filter.update(sample(5000, 5.0)), 0.0)
        assertEquals(10.0, filter.update(sample(10_000, 10.0)), 0.0001)
    }

    @Test
    fun stoppingSuppressesImmediatelyAndDepartureDoesNotBridgeDrift() {
        val filter = filter()
        filter.update(sample(0, 0.0))
        filter.update(sample(1000, 1.0))
        assertEquals(2.0, filter.update(sample(2000, 2.0)), 0.0001)
        assertEquals(0.0, filter.update(sample(3000, 100.0, 0f)), 0.0)
        assertFalse(filter.isMoving)
        assertEquals(0.0, filter.update(sample(4000, 200.0)), 0.0)
        assertEquals(0.0, filter.update(sample(5000, 201.0)), 0.0)
        assertEquals(2.0, filter.update(sample(6000, 202.0)), 0.0001)
    }

    @Test
    fun rejectedCandidateDiscardsUnconfirmedDistance() {
        val filter = filter()
        filter.update(sample(0, 0.0))
        filter.update(sample(1000, 50.0))
        filter.update(sample(2000, 100.0, 1f, 2f))
        assertEquals(0.0, filter.update(sample(3000, 200.0)), 0.0)
        assertEquals(0.0, filter.update(sample(4000, 201.0)), 0.0)
        assertEquals(2.0, filter.update(sample(5000, 202.0)), 0.0001)
    }

    @Test
    fun missingAccuracyUsesSpeedAndConfirmation() {
        val filter = filter()
        assertEquals(0.0, filter.update(sample(0, 0.0, accuracy = null)), 0.0)
        assertEquals(0.0, filter.update(sample(1000, 1.0, accuracy = null)), 0.0)
        assertEquals(2.0, filter.update(sample(2000, 2.0, accuracy = null)), 0.0001)
    }

    @Test
    fun missingOrInvalidSpeedCannotConfirmMovement() {
        for (speed in listOf(null, Float.NaN, Float.POSITIVE_INFINITY, -1f, 0.55f)) {
            val filter = filter()
            repeat(3) { second ->
                assertEquals(0.0, filter.update(sample(second * 1000L, second.toDouble(), speed)), 0.0)
            }
            assertFalse(filter.isMoving)
        }
    }

    @Test
    fun invalidAccuracyIsRejectedButZeroAccuracyIsValid() {
        for (accuracy in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f)) {
            val filter = filter()
            repeat(3) { second ->
                assertEquals(0.0, filter.update(sample(second * 1000L, second.toDouble(), accuracy = accuracy)), 0.0)
            }
        }
        val filter = filter()
        filter.update(sample(0, 0.0, accuracy = 0f))
        filter.update(sample(1000, 1.0, accuracy = 0f))
        assertEquals(2.0, filter.update(sample(2000, 2.0, accuracy = 0f)), 0.0001)
    }

    @Test
    fun speedThresholdIsTwoKilometresPerHourInBothPaths() {
        val filter = filter()
        val speed = (2.0 / 3.6).toFloat()
        filter.update(sample(0, 0.0, speed))
        filter.update(sample(1000, 0.6, speed))
        assertEquals(1.2, filter.update(sample(2000, 1.2, speed)), 0.0001)
    }

    @Test
    fun resetRequiresConfirmationAndDoesNotBridgeAnInterruption() {
        for (confirmed in listOf(false, true)) {
            val filter = filter()
            filter.update(sample(0, 0.0))
            filter.update(sample(1000, 1.0))
            if (confirmed) filter.update(sample(2000, 2.0))
            filter.reset()
            assertFalse(filter.isMoving)
            assertEquals(0.0, filter.update(sample(10_000, 100.0)), 0.0)
            assertEquals(0.0, filter.update(sample(11_000, 101.0)), 0.0)
            assertEquals(2.0, filter.update(sample(12_000, 102.0)), 0.0001)
        }
    }

    @Test
    fun duplicateAndOutOfOrderFixesCannotConfirmOrChangeTheAnchor() {
        val filter = filter()
        filter.update(sample(1000, 0.0))
        assertEquals(0.0, filter.update(sample(1000, 100.0)), 0.0)
        assertEquals(0.0, filter.update(sample(500, 200.0)), 0.0)
        assertEquals(0.0, filter.update(sample(3000, 2.0)), 0.0)
        assertEquals(3.0, filter.update(sample(4000, 3.0)), 0.0001)
    }

    @Test
    fun invalidCoordinatesBreakTheMovementChain() {
        val filter = filter()
        filter.update(sample(0, 0.0))
        filter.update(sample(1000, 1.0))
        assertEquals(0.0, filter.update(sample(2000, 2.0).copy(latitude = Double.NaN)), 0.0)
        assertEquals(0.0, filter.update(sample(3000, 100.0)), 0.0)
        assertEquals(0.0, filter.update(sample(4000, 101.0)), 0.0)
        assertEquals(2.0, filter.update(sample(5000, 102.0)), 0.0001)
    }

    @Test
    fun androidAdapterHonoursPresenceFlags() {
        val location = Mockito.mock(Location::class.java)
        Mockito.`when`(location.latitude).thenReturn(48.0)
        Mockito.`when`(location.speed).thenReturn(1f)
        Mockito.`when`(location.speedAccuracyMetersPerSecond).thenReturn(5f)
        val filter = filter()
        repeat(3) { second ->
            Mockito.`when`(location.elapsedRealtimeNanos).thenReturn(second * 1_000_000_000L)
            Mockito.`when`(location.longitude).thenReturn(second * 0.001)
            assertEquals(0.0, filter.update(location), 0.0) // hasSpeed is false
        }
        Mockito.`when`(location.hasSpeed()).thenReturn(true)
        // hasSpeedAccuracy is false: ignore the getter's value and use the fallback.
        repeat(3) { index ->
            Mockito.`when`(location.elapsedRealtimeNanos).thenReturn((index + 3) * 1_000_000_000L)
            Mockito.`when`(location.longitude).thenReturn(index * 0.001)
            assertEquals(if (index == 2) 2.0 else 0.0, filter.update(location), 0.0001)
        }
        Mockito.`when`(location.hasSpeedAccuracy()).thenReturn(true)
        Mockito.`when`(location.elapsedRealtimeNanos).thenReturn(6_000_000_000L)
        assertEquals(0.0, filter.update(location), 0.0)
        assertFalse(filter.isMoving)
    }
}
