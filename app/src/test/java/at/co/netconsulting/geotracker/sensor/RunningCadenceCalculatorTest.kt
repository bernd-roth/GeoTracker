package at.co.netconsulting.geotracker.sensor

import org.junit.Assert.assertEquals
import org.junit.Test

class RunningCadenceCalculatorTest {
    @Test
    fun `converts 160 steps per minute to Garmin cadence 80`() {
        val calculator = RunningCadenceCalculator()
        val stepInterval = 375_000_000L

        repeat(20) { index -> calculator.addStep(1_000_000_000L + index * stepInterval) }

        assertEquals(80, calculator.cadenceAt(1_000_000_000L + 19 * stepInterval))
    }

    @Test
    fun `returns zero until enough steps are available`() {
        val calculator = RunningCadenceCalculator()
        calculator.addStep(1_000_000_000L)
        calculator.addStep(1_400_000_000L)
        calculator.addStep(1_800_000_000L)

        assertEquals(0, calculator.cadenceAt(1_800_000_000L))
    }

    @Test
    fun `returns zero when the last step is stale`() {
        val calculator = RunningCadenceCalculator()
        repeat(8) { index -> calculator.addStep(1_000_000_000L + index * 400_000_000L) }

        assertEquals(0, calculator.cadenceAt(7_000_000_000L))
    }

    @Test
    fun `reset clears the rolling window`() {
        val calculator = RunningCadenceCalculator()
        repeat(8) { index -> calculator.addStep(1_000_000_000L + index * 400_000_000L) }

        calculator.reset()

        assertEquals(0, calculator.cadenceAt(4_000_000_000L))
    }
}
