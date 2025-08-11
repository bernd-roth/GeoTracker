package at.co.netconsulting.geotracker.data

data class GpxImportProgress(
    val isImporting: Boolean = false,
    val progress: Float = 0f,
    val pointsProcessed: Int = 0,
    val totalEstimated: Int = 0,
    val status: String = ""
)