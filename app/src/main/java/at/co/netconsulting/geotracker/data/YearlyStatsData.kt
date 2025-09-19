package at.co.netconsulting.geotracker.data

data class YearlyStatsData(
    val year: Int,
    val totalDistance: Double, // in kilometers
    val totalActivities: Int,
    val averageDistancePerActivity: Double,
    val averageDistancePerMonth: Double,
    val monthlyStats: List<MonthlyStats>,
    val weeklyStats: List<WeeklyStats>,
    val heartRateStats: HeartRateYearlyStats?
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