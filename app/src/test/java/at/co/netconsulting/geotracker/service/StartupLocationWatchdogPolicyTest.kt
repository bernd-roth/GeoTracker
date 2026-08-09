package at.co.netconsulting.geotracker.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupLocationWatchdogPolicyTest {

    @Test
    fun initialCheckRequiresRepeatedCallbacks() {
        assertTrue(StartupLocationWatchdogPolicy.shouldRestartInitially(0L))
        assertTrue(StartupLocationWatchdogPolicy.shouldRestartInitially(1L))
        assertFalse(StartupLocationWatchdogPolicy.shouldRestartInitially(2L))
    }

    @Test
    fun retryRequiresTwoNewCallbacksAfterRestart() {
        assertTrue(StartupLocationWatchdogPolicy.shouldRetryAfterRestart(1L, 1L))
        assertTrue(StartupLocationWatchdogPolicy.shouldRetryAfterRestart(1L, 2L))
        assertFalse(StartupLocationWatchdogPolicy.shouldRetryAfterRestart(1L, 3L))
    }
}
