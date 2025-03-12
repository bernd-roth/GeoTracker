package at.co.netconsulting.geotracker.data

data class TotalStatistics(
    val totalDistanceKm: Double,
    val distanceByYear: Map<String, Double>
)