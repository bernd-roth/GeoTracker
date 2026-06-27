package at.co.netconsulting.geotracker.data

import at.co.netconsulting.geotracker.tools.LocalDateTimeAdapter
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class MetricsCadenceSerializationTest {
    @Test
    fun `websocket metrics serialize raw cadence`() {
        val timestamp = LocalDateTime.of(2026, 6, 27, 10, 30)
        val metrics = Metrics(
            latitude = 48.2082,
            longitude = 16.3738,
            speed = 12.5f,
            altitude = 180.0,
            startDateTime = timestamp,
            currentDateTime = timestamp,
            sessionId = "cadence-test",
            sportType = "Running",
            cadence = 82,
            movingAverageSpeed = 12.0
        )

        val json = GsonBuilder()
            .registerTypeAdapter(LocalDateTime::class.java, LocalDateTimeAdapter())
            .create()
            .toJsonTree(metrics)

        assertEquals(82, json.asJsonObject.get("cadence").asInt)
    }
}
