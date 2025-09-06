package at.co.netconsulting.geotracker.data

data class BackupProgress(
    val isBackingUp: Boolean = false,
    val overallProgress: Float = 0f,
    val currentPhase: BackupPhase = BackupPhase.IDLE,
    val filesProcessed: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
    val databaseProgress: Float = 0f,
    val status: String = ""
) {
    /**
     * Get a formatted file progress string like "11/128"
     */
    fun getFileProgressText(): String {
        return if (totalFiles > 0) "$filesProcessed/$totalFiles" else ""
    }
    
    /**
     * Get overall progress percentage (0-100)
     */
    fun getProgressPercentage(): Int {
        return (overallProgress * 100).toInt()
    }
}

enum class BackupPhase {
    IDLE,
    INITIALIZING,
    BACKING_UP_DATABASE,
    CLEARING_OLD_FILES,
    EXPORTING_GPX_FILES,
    COMPLETED,
    FAILED
}