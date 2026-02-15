package at.co.netconsulting.geotracker.data

data class MediaUploadProgress(
    val isUploading: Boolean = false,
    val currentFile: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
    val currentFileProgress: Float = 0f,
    val status: String = ""
)
