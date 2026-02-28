package at.co.netconsulting.geotracker.composables

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Waypoint
import at.co.netconsulting.geotracker.domain.WaypointPhoto
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun WaypointPopupDialog(
    waypoint: Waypoint,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val database = remember { FitnessTrackerDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()

    var photos by remember { mutableStateOf(listOf<WaypointPhoto>()) }

    // Load existing photos for this waypoint
    LaunchedEffect(waypoint.waypointId) {
        photos = withContext(Dispatchers.IO) {
            database.waypointPhotoDao().getPhotosForWaypoint(waypoint.waypointId)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val newPhotos = mutableListOf<WaypointPhoto>()
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        try {
                            val path = copyPhotoToStorage(context, uri)
                            val photoId = database.waypointPhotoDao().insertPhoto(
                                WaypointPhoto(waypointId = waypoint.waypointId, photoPath = path)
                            )
                            newPhotos.add(WaypointPhoto(photoId = photoId, waypointId = waypoint.waypointId, photoPath = path))
                        } catch (e: Exception) {
                            Timber.e(e, "Error saving waypoint photo")
                        }
                    }
                }
                photos = photos + newPhotos
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(waypoint.name, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Description
                if (!waypoint.description.isNullOrEmpty()) {
                    Text(waypoint.description, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Photos section label
                Text("Photos", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(6.dp))

                // Thumbnail row
                if (photos.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(photos) { photo ->
                            AsyncImage(
                                model = photo.photoPath,
                                contentDescription = "Waypoint photo",
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Add Photos button
                OutlinedButton(
                    onClick = { photoPickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Add Photos",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Photos")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
