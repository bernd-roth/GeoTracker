package at.co.netconsulting.geotracker.data

data class YearlyStatsData(
    val year: Int,
    val totalDistance: Double, // in kilometers
    val totalActivities: Int,
    val averageDistancePerActivity: Double,
    val averageDistancePerMonth: Double,
    val monthlyStats: List<MonthlyStats>,
    val weeklyStats: List<WeeklyStats>,
    val heartRateStats: HeartRateYearlyStats?,
    val cadenceStats: CadenceYearlyStats? = null
)

data class MonthlyStats(
    val year: Int,
    val month: Int, // 1-12
    val totalDistance: Double, // in kilometers
    val activityCount: Int
)

data class WeeklyStats(
    val year: Int,
    val week: Int, // week of year
    val totalDistance: Double, // in kilometers
    val activityCount: Int
)

data class HeartRateYearlyStats(
    val year: Int,
    val overallMinHR: Int,
    val overallMaxHR: Int,
    val overallAvgHR: Double,
    val activitiesWithHR: Int,
    val totalActivities: Int,
    val monthlyHeartRateStats: List<MonthlyHeartRateStats>,
    val heartRateZoneDistribution: HeartRateZoneStats
)

data class MonthlyHeartRateStats(
    val year: Int,
    val month: Int,
    val minHR: Int,
    val maxHR: Int,
    val avgHR: Double,
    val activitiesWithHR: Int
)

data class HeartRateZoneStats(
    val zone1Count: Int, // Recovery zone (50-60% max HR)
    val zone2Count: Int, // Aerobic base (60-70% max HR)
    val zone3Count: Int, // Aerobic zone (70-80% max HR)
    val zone4Count: Int, // Lactate threshold (80-90% max HR)
    val zone5Count: Int  // Neuromuscular power (90-100% max HR)
)

data class CadenceYearlyStats(
    val year: Int,
    val overallMinSpm: Int,
    val overallAvgSpm: Double,
    val overallMaxSpm: Int,
    val activitiesWithCadence: Int,
    val totalRunningActivities: Int,
    val monthlyCadenceStats: List<MonthlyCadenceStats>,
    val distribution: List<CadenceBucketStats>
)

data class MonthlyCadenceStats(
    val year: Int,
    val month: Int,
    val minSpm: Int,
    val avgSpm: Double,
    val maxSpm: Int,
    val activitiesWithCadence: Int
)

data class CadenceBucketStats(
    val label: String,
    val sampleCount: Int
)
