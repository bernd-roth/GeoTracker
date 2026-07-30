package at.co.netconsulting.geotracker.composables

import kotlin.test.Test
import kotlin.test.assertEquals

class YearlyCadenceStatsTest {
    @Test
    fun `spm distribution assigns boundary values to exactly one bucket`() {
        val buckets = cadenceDistributionFor(
            listOf(139, 140, 149, 150, 159, 160, 169, 170, 179, 180)
        )

        assertEquals(
            listOf(1, 2, 2, 2, 2, 1),
            buckets.map { it.sampleCount }
        )
        assertEquals(10, buckets.sumOf { it.sampleCount })
    }
}
