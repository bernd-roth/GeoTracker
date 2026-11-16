package at.co.netconsulting.geotracker.service

import java.io.IOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDownloadRetryTest {
    @Test fun `network failures stop retrying after three attempts`() {
        assertTrue(SessionDownloadWorker.shouldRetry(IOException("offline"), 0))
        assertTrue(SessionDownloadWorker.shouldRetry(IOException("offline"), 1))
        assertFalse(SessionDownloadWorker.shouldRetry(IOException("offline"), 2))
        assertFalse(SessionDownloadWorker.shouldRetry(IOException("offline"), 100))
    }

    @Test fun `temporary server failures retry but missing and unauthorized events do not`() {
        for (code in listOf(408, 425, 429, 500, 503)) {
            assertTrue(SessionDownloadWorker.shouldRetry(Exception("API error: $code"), 0))
            assertFalse(SessionDownloadWorker.shouldRetry(Exception("API error: $code"), 2))
        }
        for (code in listOf(400, 401, 403, 404)) {
            assertFalse(SessionDownloadWorker.shouldRetry(Exception("API error: $code"), 0))
        }
        assertFalse(SessionDownloadWorker.shouldRetry(IllegalStateException("Invalid data"), 0))
    }
}
