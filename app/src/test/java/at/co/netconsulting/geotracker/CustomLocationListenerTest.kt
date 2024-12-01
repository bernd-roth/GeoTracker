package at.co.netconsulting.geotracker

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomLocationListenerTest {

    @Test
    fun testCalculateLap() {
        val mockListener = MockCustomLocationListener()

        val distances = listOf(200.0, 500.0, 300.0, 1000.0, 600.0)
        val expectedLaps = listOf(0, 0, 1, 2, 2)

        for (i in distances.indices) {
            val lapCount = mockListener.calculateLap(distances[i])
            assertEquals(expectedLaps[i], lapCount)
        }
    }
}

class MockCustomLocationListener {
    private var lapCounter: Double = 0.0
    private var lap: Int = 0

    fun calculateLap(distanceIncrement: Double): Int {
        lapCounter += distanceIncrement
        if (lapCounter >= 1000) {
            lap += 1
            lapCounter -= 1000
        }
        return lap
    }
}