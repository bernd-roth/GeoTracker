package at.co.netconsulting.geotracker.composables

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import at.co.netconsulting.geotracker.data.EventWithDetails
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.tools.Tools
import at.co.netconsulting.geotracker.util.WorkoutOverlayRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutShareScreen(
    eventId: Int,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { FitnessTrackerDatabase.getInstance(context) }

    var eventWithDetails by remember { mutableStateOf<EventWithDetails?>(null) }
    var isLoadingEvent by remember { mutableStateOf(true) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var overlaidBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Load event details
    LaunchedEffect(eventId) {
        withContext(Dispatchers.IO) {
            try {
                val event = database.eventDao().getEventById(eventId)
                if (event != null) {
                    val metrics = database.metricDao().getMetricsForEvent(eventId)
                    val timeRange = database.metricDao().getEventTimeRange(eventId)
                    val maxDistance = database.metricDao().getMaxDistanceForEvent(eventId) ?: 0.0

                    val startTime = timeRange?.minTime ?: 0L
                    val endTime = timeRange?.maxTime ?: 0L

                    // Calculate elevation gain from consecutive points
                    var elevGain = 0.0
                    var elevLoss = 0.0
                    if (metrics.size > 1) {
                        for (i in 1 until metrics.size) {
                            val diff = metrics[i].elevation.toDouble() - metrics[i - 1].elevation.toDouble()
                            if (diff > 0) elevGain += diff else elevLoss -= diff
                        }
                    }

                    // Calculate average speed in km/h
                    val durationHours = (endTime - startTime) / 3600000.0
                    val distanceKm = maxDistance / 1000.0
                    val avgSpeed = if (durationHours > 0) distanceKm / durationHours else 0.0

                    // Heart rate stats
                    val heartRates = metrics.map { it.heartRate }.filter { it > 0 }
                    val avgHr = if (heartRates.isNotEmpty()) heartRates.average().toInt() else 0

                    eventWithDetails = EventWithDetails(
                        event = event,
                        hasFullDetails = true,
                        totalDistance = maxDistance,
                        averageSpeed = avgSpeed,
                        startTime = startTime,
                        endTime = endTime,
                        elevationGain = elevGain,
                        elevationLoss = elevLoss,
                        avgHeartRate = avgHr
                    )
                }
            } catch (e: Exception) {
                Log.e("WorkoutShareScreen", "Error loading event: ${e.message}")
            }
            isLoadingEvent = false
        }
    }

    // Camera temp file URI
    val cameraFileUri = remember { mutableStateOf<Uri?>(null) }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedPhotoUri = it
            overlaidBitmap = null
        }
    }

    // Camera capture
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraFileUri.value?.let {
                selectedPhotoUri = it
                overlaidBitmap = null
            }
        }
    }

    // Camera permission
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera(context, cameraFileUri, cameraLauncher)
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    // Process overlay when photo is selected
    LaunchedEffect(selectedPhotoUri, eventWithDetails) {
        val uri = selectedPhotoUri ?: return@LaunchedEffect
        val event = eventWithDetails ?: return@LaunchedEffect

        isProcessing = true
        withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeSampledBitmap(context, uri, 2048)
                if (bitmap != null) {
                    overlaidBitmap = WorkoutOverlayRenderer.renderOverlay(bitmap, event)
                }
            } catch (e: Exception) {
                Log.e("WorkoutShareScreen", "Error processing image: ${e.message}")
            }
        }
        isProcessing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share Workout") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoadingEvent -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                eventWithDetails == null -> {
                    Text(
                        text = "Could not load event data",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                overlaidBitmap != null -> {
                    // Preview with overlay
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Image preview
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.Black)
                        ) {
                            Image(
                                bitmap = overlaidBitmap!!.asImageBitmap(),
                                contentDescription = "Workout photo with stats overlay",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center),
                                    color = Color.White
                                )
                            }
                        }

                        // Bottom action bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Change photo button
                            OutlinedButton(
                                onClick = {
                                    selectedPhotoUri = null
                                    overlaidBitmap = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Change Photo")
                            }

                            // Share button
                            Button(
                                onClick = {
                                    overlaidBitmap?.let { bitmap ->
                                        coroutineScope.launch {
                                            shareImage(context, bitmap, eventWithDetails!!)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Share")
                            }
                        }
                    }
                }

                else -> {
                    // Photo source selection
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Event summary
                        val event = eventWithDetails!!
                        Text(
                            text = event.event.artOfSport.ifEmpty { "Workout" },
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = event.event.eventName,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val durationMs = event.endTime - event.startTime
                        if (durationMs > 0) {
                            Text(
                                text = "${String.format("%.2f", event.totalDistance / 1000)} km  |  ${Tools().formatDuration(durationMs)}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        Text(
                            text = "Choose a photo to overlay your workout stats",
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // Gallery button
                        Button(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Choose from Gallery", fontSize = 16.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Camera button
                        OutlinedButton(
                            onClick = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Take a Photo", fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun launchCamera(
    context: Context,
    cameraFileUri: MutableState<Uri?>,
    cameraLauncher: androidx.activity.result.ActivityResultLauncher<Uri>
) {
    val shareDir = File(context.cacheDir, "share_images")
    shareDir.mkdirs()
    val tempFile = File(shareDir, "camera_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
    cameraFileUri.value = uri
    cameraLauncher.launch(uri)
}

private fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
    return try {
        // First pass: get dimensions
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        // Calculate sample size
        val (width, height) = options.outWidth to options.outHeight
        var sampleSize = 1
        while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        // Second pass: decode with sample size
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: return null

        // Read EXIF orientation and rotate if needed
        val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) bitmap.recycle()
            rotated
        } else {
            bitmap
        }
    } catch (e: Exception) {
        Log.e("WorkoutShareScreen", "Error decoding bitmap: ${e.message}")
        null
    }
}

private suspend fun shareImage(context: Context, bitmap: Bitmap, event: EventWithDetails) {
    withContext(Dispatchers.IO) {
        try {
            val shareDir = File(context.cacheDir, "share_images")
            shareDir.mkdirs()
            // Clean up old share images
            shareDir.listFiles()?.filter {
                it.name.startsWith("geotracker_workout_") &&
                        System.currentTimeMillis() - it.lastModified() > 3600000
            }?.forEach { it.delete() }

            val shareFile = File(shareDir, "geotracker_workout_${System.currentTimeMillis()}.jpg")
            FileOutputStream(shareFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }

            val shareUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", shareFile
            )

            val distanceStr = String.format("%.2f km", event.totalDistance / 1000)
            val sportType = event.event.artOfSport.ifEmpty { "Workout" }

            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "My $sportType - $distanceStr tracked with GeoTracker!"
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Workout"))
            }
        } catch (e: Exception) {
            Log.e("WorkoutShareScreen", "Error sharing image: ${e.message}")
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Error sharing image", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
