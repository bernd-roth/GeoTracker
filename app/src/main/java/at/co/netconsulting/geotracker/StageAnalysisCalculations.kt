package at.co.netconsulting.geotracker

import at.co.netconsulting.geotracker.domain.Metric

/** Each recording contributes its own elapsed duration; gaps between recordings never contribute. */
data class StageTotals(
    val distanceMeters: Double = 0.0,
    val durationMillis: Long = 0,
    val ascentMeters: Double = 0.0,
    val descentMeters: Double = 0.0,
    val maxSpeedKmh: Double = 0.0
) {
    val averageSpeedKmh: Double? get() = if (durationMillis > 0) distanceMeters * 3600 / durationMillis else null
}

fun calculateStageTotals(metrics: List<Metric>): StageTotals {
    val sorted = metrics.sortedBy { it.timeInMilliseconds }
    var ascent = 0.0
    var descent = 0.0
    sorted.zipWithNext().forEach { (a, b) ->
        val difference = (b.elevation - a.elevation).toDouble()
        if (difference.isFinite()) {
            if (difference > 0) ascent += difference else descent -= difference
        }
    }
    return StageTotals(
        distanceMeters = sorted.map { it.distance }.filter { it.isFinite() && it >= 0 }.maxOrNull() ?: 0.0,
        durationMillis = if (sorted.size < 2) 0 else (sorted.last().timeInMilliseconds - sorted.first().timeInMilliseconds).coerceAtLeast(0),
        ascentMeters = ascent,
        descentMeters = descent,
        maxSpeedKmh = (sorted.map { it.speed.toDouble() }.filter { it.isFinite() && it >= 0 }.maxOrNull() ?: 0.0) * 3.6
    )
}

fun combineStageTotals(stages: List<StageTotals>) = StageTotals(
    distanceMeters = stages.sumOf { it.distanceMeters },
    durationMillis = stages.sumOf { it.durationMillis },
    ascentMeters = stages.sumOf { it.ascentMeters },
    descentMeters = stages.sumOf { it.descentMeters },
    maxSpeedKmh = stages.maxOfOrNull { it.maxSpeedKmh } ?: 0.0
)
