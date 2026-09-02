package at.co.netconsulting.geotracker.achievements

import at.co.netconsulting.geotracker.data.SportCatalog
import java.util.Locale
import kotlin.math.roundToLong

enum class AchievementKind {
    DISTANCE,
    DURATION
}

data class AchievementDefinition(
    val label: String,
    val kind: AchievementKind,
    val target: Double
)

data class AchievementSample(
    val timeMillis: Long,
    val distanceMeters: Double
)

data class AchievementActivity(
    val eventId: Int,
    val eventName: String,
    val eventDate: String,
    val sport: String,
    val sportFamily: String? = null,
    val discipline: String? = null,
    val eventFormat: String? = null,
    val samples: List<AchievementSample>
)

data class AchievementRecord(
    val definition: AchievementDefinition,
    val eventId: Int,
    val eventName: String,
    val eventDate: String,
    val discipline: String?,
    val eventFormat: String?,
    /** Elapsed time for a distance target, distance covered for a duration target. */
    val value: Double
)

data class SportAchievements(
    val sport: String,
    val activityCount: Int,
    val totalDistanceMeters: Double,
    val distanceDefinitions: List<AchievementDefinition>,
    val durationDefinitions: List<AchievementDefinition>,
    val records: Map<AchievementDefinition, AchievementRecord>
)

object AchievementCalculator {
    private const val MAX_CONTINUOUS_SAMPLE_GAP_MILLIS = 60L * 60 * 1_000

    val distanceDefinitions = listOf(
        AchievementDefinition("1 km", AchievementKind.DISTANCE, 1_000.0),
        AchievementDefinition("5 km", AchievementKind.DISTANCE, 5_000.0),
        AchievementDefinition("10 km", AchievementKind.DISTANCE, 10_000.0),
        AchievementDefinition("Half marathon", AchievementKind.DISTANCE, 21_097.5),
        AchievementDefinition("Marathon", AchievementKind.DISTANCE, 42_195.0),
        AchievementDefinition("50 km", AchievementKind.DISTANCE, 50_000.0),
        AchievementDefinition("100 km", AchievementKind.DISTANCE, 100_000.0)
    )

    private val genericDistanceDefinitions = listOf(
        AchievementDefinition("1 km", AchievementKind.DISTANCE, 1_000.0),
        AchievementDefinition("5 km", AchievementKind.DISTANCE, 5_000.0),
        AchievementDefinition("10 km", AchievementKind.DISTANCE, 10_000.0),
        AchievementDefinition("21.1 km", AchievementKind.DISTANCE, 21_097.5),
        AchievementDefinition("42.2 km", AchievementKind.DISTANCE, 42_195.0),
        AchievementDefinition("50 km", AchievementKind.DISTANCE, 50_000.0),
        AchievementDefinition("100 km", AchievementKind.DISTANCE, 100_000.0)
    )

    val durationDefinitions = listOf(
        AchievementDefinition("6 h", AchievementKind.DURATION, (6L * 60 * 60 * 1_000).toDouble()),
        AchievementDefinition("12 h", AchievementKind.DURATION, (12L * 60 * 60 * 1_000).toDouble()),
        AchievementDefinition("24 h", AchievementKind.DURATION, (24L * 60 * 60 * 1_000).toDouble()),
        AchievementDefinition("48 h", AchievementKind.DURATION, (48L * 60 * 60 * 1_000).toDouble()),
        AchievementDefinition("72 h", AchievementKind.DURATION, (72L * 60 * 60 * 1_000).toDouble()),
        AchievementDefinition("6 days", AchievementKind.DURATION, (6L * 24 * 60 * 60 * 1_000).toDouble())
    )

    fun calculate(activities: List<AchievementActivity>): List<SportAchievements> {
        return activities
            .groupBy { activity ->
                resolvedMetadata(activity).family.trim().ifEmpty { "Other" }.lowercase(Locale.ROOT)
            }
            .values
            .map { sportActivities -> calculateSport(sportActivities) }
            .sortedWith(
                compareBy<SportAchievements> { !it.sport.equals("Running", ignoreCase = true) }
                    .thenBy { it.sport.lowercase(Locale.ROOT) }
            )
    }

    fun bestElapsedTimeMillis(
        samples: List<AchievementSample>,
        targetDistanceMeters: Double
    ): Long? {
        if (!targetDistanceMeters.isFinite() || targetDistanceMeters <= 0.0) return null
        return continuousSegments(normalize(samples))
            .mapNotNull { bestElapsedTimeForNormalizedSamples(it, targetDistanceMeters) }
            .minOrNull()
    }

    private fun bestElapsedTimeForNormalizedSamples(
        points: List<AchievementSample>,
        targetDistanceMeters: Double
    ): Long? {
        if (
            points.size < 2 ||
            points.last().distanceMeters - points.first().distanceMeters < targetDistanceMeters
        ) {
            return null
        }

        var best = Long.MAX_VALUE

        // Candidate windows whose start is a recorded sample. The end is interpolated.
        var endIndex = 1
        for (startIndex in 0 until points.lastIndex) {
            if (endIndex <= startIndex) endIndex = startIndex + 1
            val target = points[startIndex].distanceMeters + targetDistanceMeters
            while (endIndex < points.size && points[endIndex].distanceMeters < target) {
                endIndex++
            }
            if (endIndex >= points.size) break

            val endTime = interpolateTimeAtDistance(
                points[endIndex - 1],
                points[endIndex],
                target
            )
            val elapsed = endTime - points[startIndex].timeMillis
            if (elapsed > 0L && elapsed < best) best = elapsed
        }

        // Candidate windows whose end is a recorded sample. The start is interpolated.
        var startIndex = 0
        for (candidateEnd in 1 until points.size) {
            val target = points[candidateEnd].distanceMeters - targetDistanceMeters
            if (target < points.first().distanceMeters) continue
            while (
                startIndex + 1 < candidateEnd &&
                points[startIndex + 1].distanceMeters <= target
            ) {
                startIndex++
            }

            val startTime = if (points[startIndex].distanceMeters == target) {
                points[startIndex].timeMillis
            } else {
                interpolateTimeAtDistance(points[startIndex], points[startIndex + 1], target)
            }
            val elapsed = points[candidateEnd].timeMillis - startTime
            if (elapsed > 0L && elapsed < best) best = elapsed
        }

        return best.takeIf { it != Long.MAX_VALUE }
    }

    fun bestDistanceMeters(
        samples: List<AchievementSample>,
        targetDurationMillis: Long
    ): Double? {
        if (targetDurationMillis <= 0L) return null
        return continuousSegments(normalize(samples))
            .mapNotNull { bestDistanceForNormalizedSamples(it, targetDurationMillis) }
            .maxOrNull()
    }

    private fun bestDistanceForNormalizedSamples(
        points: List<AchievementSample>,
        targetDurationMillis: Long
    ): Double? {
        if (
            points.size < 2 ||
            points.last().timeMillis - points.first().timeMillis < targetDurationMillis
        ) {
            return null
        }

        var best = Double.NEGATIVE_INFINITY

        // Candidate windows whose start is a recorded sample. The end is interpolated.
        var endIndex = 1
        for (startIndex in 0 until points.lastIndex) {
            val targetTime = points[startIndex].timeMillis + targetDurationMillis
            if (targetTime > points.last().timeMillis) break
            if (endIndex <= startIndex) endIndex = startIndex + 1
            while (endIndex < points.size && points[endIndex].timeMillis < targetTime) {
                endIndex++
            }
            if (endIndex >= points.size) break
            val endDistance = interpolateDistanceAtTime(
                points[endIndex - 1],
                points[endIndex],
                targetTime
            )
            best = maxOf(best, endDistance - points[startIndex].distanceMeters)
        }

        // Candidate windows whose end is a recorded sample. The start is interpolated.
        var startIndex = 0
        for (candidateEnd in 1 until points.size) {
            val targetTime = points[candidateEnd].timeMillis - targetDurationMillis
            if (targetTime < points.first().timeMillis) continue
            while (
                startIndex + 1 < candidateEnd &&
                points[startIndex + 1].timeMillis <= targetTime
            ) {
                startIndex++
            }
            val startDistance = if (points[startIndex].timeMillis == targetTime) {
                points[startIndex].distanceMeters
            } else {
                interpolateDistanceAtTime(points[startIndex], points[startIndex + 1], targetTime)
            }
            best = maxOf(best, points[candidateEnd].distanceMeters - startDistance)
        }

        return best.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun calculateSport(activities: List<AchievementActivity>): SportAchievements {
        val displayName = activities
            .map { resolvedMetadata(it).family.trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: "Other"
        val sportDistanceDefinitions = if (displayName.equals(SportCatalog.RUNNING, ignoreCase = true)) {
            distanceDefinitions
        } else {
            genericDistanceDefinitions
        }
        val normalizedActivities = activities.map { activity ->
            activity to normalize(activity.samples)
        }
        val records = mutableMapOf<AchievementDefinition, AchievementRecord>()

        normalizedActivities.forEach { (activity, samples) ->
            val segments = continuousSegments(samples)
            sportDistanceDefinitions.forEach distanceDefinition@{ definition ->
                val elapsed = segments
                    .mapNotNull { bestElapsedTimeForNormalizedSamples(it, definition.target) }
                    .minOrNull()
                    ?: return@distanceDefinition
                val existing = records[definition]
                if (existing == null || elapsed < existing.value) {
                    records[definition] = activity.record(definition, elapsed.toDouble())
                }
            }
            durationDefinitions.forEach durationDefinition@{ definition ->
                val distance = segments
                    .mapNotNull { bestDistanceForNormalizedSamples(it, definition.target.roundToLong()) }
                    .maxOrNull()
                    ?: return@durationDefinition
                val existing = records[definition]
                if (existing == null || distance > existing.value) {
                    records[definition] = activity.record(definition, distance)
                }
            }
        }

        return SportAchievements(
            sport = displayName,
            activityCount = activities.size,
            totalDistanceMeters = normalizedActivities.sumOf { (_, samples) ->
                samples.lastOrNull()?.distanceMeters ?: 0.0
            },
            distanceDefinitions = sportDistanceDefinitions,
            durationDefinitions = durationDefinitions,
            records = records
        )
    }

    private fun AchievementActivity.record(
        definition: AchievementDefinition,
        value: Double
    ) = AchievementRecord(
        definition = definition,
        eventId = eventId,
        eventName = eventName,
        eventDate = eventDate,
        discipline = resolvedMetadata(this).discipline,
        eventFormat = resolvedMetadata(this).eventFormat,
        value = value
    )

    private fun continuousSegments(points: List<AchievementSample>): List<List<AchievementSample>> {
        if (points.isEmpty()) return emptyList()

        val segments = mutableListOf<MutableList<AchievementSample>>()
        var current = mutableListOf(points.first())
        for (index in 1 until points.size) {
            val point = points[index]
            val gap = point.timeMillis - points[index - 1].timeMillis
            if (gap > MAX_CONTINUOUS_SAMPLE_GAP_MILLIS) {
                segments.add(current)
                current = mutableListOf(point)
            } else {
                current += point
            }
        }
        segments.add(current)
        return segments
    }

    private fun normalize(samples: List<AchievementSample>): List<AchievementSample> {
        val sorted = samples
            .asSequence()
            .filter { it.timeMillis >= 0L && it.distanceMeters.isFinite() && it.distanceMeters >= 0.0 }
            .sortedWith(compareBy<AchievementSample> { it.timeMillis }.thenBy { it.distanceMeters })
            .toList()
        if (sorted.isEmpty()) return emptyList()

        val valid = ArrayList<AchievementSample>(sorted.size)
        sorted.forEach { sample ->
            val previous = valid.lastOrNull()
            if (previous?.timeMillis == sample.timeMillis) {
                if (sample.distanceMeters > previous.distanceMeters) {
                    valid[valid.lastIndex] = sample
                }
            } else {
                valid += sample
            }
        }

        var furthestDistance = valid.first().distanceMeters
        return valid.map { sample ->
            furthestDistance = maxOf(furthestDistance, sample.distanceMeters)
            AchievementSample(sample.timeMillis, furthestDistance)
        }
    }

    private fun interpolateTimeAtDistance(
        before: AchievementSample,
        after: AchievementSample,
        targetDistance: Double
    ): Long {
        val distanceDelta = after.distanceMeters - before.distanceMeters
        if (distanceDelta <= 0.0) return after.timeMillis
        val fraction = ((targetDistance - before.distanceMeters) / distanceDelta).coerceIn(0.0, 1.0)
        return (before.timeMillis + (after.timeMillis - before.timeMillis) * fraction).roundToLong()
    }

    private fun interpolateDistanceAtTime(
        before: AchievementSample,
        after: AchievementSample,
        targetTime: Long
    ): Double {
        val timeDelta = after.timeMillis - before.timeMillis
        if (timeDelta <= 0L) return after.distanceMeters
        val fraction = ((targetTime - before.timeMillis).toDouble() / timeDelta).coerceIn(0.0, 1.0)
        return before.distanceMeters + (after.distanceMeters - before.distanceMeters) * fraction
    }

    private fun resolvedMetadata(activity: AchievementActivity) = SportCatalog.resolve(
        legacySport = activity.sport,
        family = activity.sportFamily,
        discipline = activity.discipline,
        eventFormat = activity.eventFormat
    )
}
