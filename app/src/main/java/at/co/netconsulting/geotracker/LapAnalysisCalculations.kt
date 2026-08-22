package at.co.netconsulting.geotracker

import at.co.netconsulting.geotracker.domain.LapTime
import kotlin.math.roundToLong

data class CalculatedLapGroup(
    val firstLapNumber: Int,
    val lastLapNumber: Int,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val totalDurationMs: Long,
    val distanceKm: Double,
    val sourceLapCount: Int,
    val isIncomplete: Boolean
) {
    val label: String
        get() = if (firstLapNumber == lastLapNumber) {
            firstLapNumber.toString()
        } else {
            "$firstLapNumber-$lastLapNumber"
        }

    val paceSecondsPerKm: Double?
        get() = if (durationMs > 0L && distanceKm > 0.0) {
            durationMs / 1000.0 / distanceKm
        } else {
            null
        }
}

fun groupLapTimes(
    lapTimes: List<LapTime>,
    requestedGroupSize: Int
): List<CalculatedLapGroup> {
    if (lapTimes.isEmpty()) return emptyList()

    val groupSize = requestedGroupSize.coerceAtLeast(1)
    val previousDistances = lapTimes.dropLast(1)
        .map { it.distance }
        .filter { it > 0.0 }
    val typicalDistance = previousDistances.takeIf { it.isNotEmpty() }?.average() ?: 0.0
    val lastLapIncomplete = lapTimes.size >= 2 && typicalDistance > 0.0 &&
            lapTimes.last().distance < typicalDistance * 0.9

    var totalDurationMs = 0L
    return lapTimes.chunked(groupSize).mapIndexed { groupIndex, members ->
        val finalMemberIndex = groupIndex * groupSize + members.lastIndex
        val durationMs = members.sumOf { it.endTime - it.startTime }
        totalDurationMs += durationMs
        CalculatedLapGroup(
            firstLapNumber = members.first().lapNumber,
            lastLapNumber = members.last().lapNumber,
            startTime = members.first().startTime,
            endTime = members.last().endTime,
            durationMs = durationMs,
            totalDurationMs = totalDurationMs,
            distanceKm = members.sumOf { it.distance },
            sourceLapCount = members.size,
            isIncomplete = members.size < groupSize ||
                    (lastLapIncomplete && finalMemberIndex == lapTimes.lastIndex)
        )
    }
}

fun calculatePace(durationMs: Long, distanceKm: Double = 1.0): String {
    if (durationMs <= 0L || distanceKm <= 0.0) return "--/km"

    val paceSecondsPerKm = (durationMs / 1000.0 / distanceKm).roundToLong()
    val minutes = paceSecondsPerKm / 60
    val seconds = paceSecondsPerKm % 60
    return String.format("%d:%02d/km", minutes, seconds)
}
