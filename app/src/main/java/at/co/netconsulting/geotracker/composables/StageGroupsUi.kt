package at.co.netconsulting.geotracker.composables

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import at.co.netconsulting.geotracker.LapAnalysisActivity
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.repository.StageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun StageGroupFields(
    groupName: String,
    stageOrder: String,
    onGroupNameChange: (String) -> Unit,
    onStageOrderChange: (String) -> Unit
) {
    val database = FitnessTrackerDatabase.getInstance(LocalContext.current)
    val groups by remember(database) { database.stageGroupDao().observeGroups() }.collectAsState(emptyList())
    var chooseGroup by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = groupName, onValueChange = onGroupNameChange,
            label = { Text("Stage group (optional)") },
            supportingText = { Text("Enter a group name or choose an existing group. Clear to ungroup.") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        if (groups.isNotEmpty()) {
            TextButton(onClick = { chooseGroup = true }) { Text("Choose existing group") }
        }
        if (groupName.isNotBlank()) {
            OutlinedTextField(
                value = stageOrder,
                onValueChange = { onStageOrderChange(it.filter(Char::isDigit).take(6)) },
                label = { Text("Stage number (automatic if empty)") },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
    }
    if (chooseGroup) AlertDialog(
        onDismissRequest = { chooseGroup = false },
        title = { Text("Stage group") },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                groups.forEach { group ->
                    TextButton(onClick = {
                        onGroupNameChange(group.name)
                        onStageOrderChange("")
                        chooseGroup = false
                    }) { Text(group.name) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { chooseGroup = false }) { Text("Close") } }
    )
}

fun stageDistance(meters: Double): String = String.format(Locale.getDefault(), "%.1f km", meters / 1000)
fun stageDuration(millis: Long): String {
    val minutes = millis / 60000
    return "${minutes / 60} h ${minutes % 60} min"
}

@Composable
fun StageGroupOverviewCard(stages: List<StageSummary>, onEdit: (Int) -> Unit, recordingEventId: Int = -1) {
    if (stages.isEmpty()) return
    val context = LocalContext.current
    val database = FitnessTrackerDatabase.getInstance(context)
    val scope = rememberCoroutineScope()
    val groupId = stages.first().event.stageGroupId!!
    var expanded by rememberSaveable(groupId) { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }) {
                Text("${if (expanded) "▼" else "▶"} ${stages.first().groupName} · ${stages.size} stages")
            }
            Text("${stages.minOf { it.event.eventDate }} – ${stages.maxOf { it.event.eventDate }}")
            Text("${stageDistance(stages.sumOf { it.distanceMeters })} · ${stageDuration(stages.sumOf { it.durationMillis })}")
            TextButton(onClick = {
                context.startActivity(Intent(context, LapAnalysisActivity::class.java)
                    .putExtra("EVENT_ID", stages.first().event.eventId).putExtra("ALL_STAGES", true))
            }) { Text("Analyse all stages") }
            if (expanded) {
                stages.forEach { stage ->
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f).clickable {
                            context.startActivity(Intent(context, LapAnalysisActivity::class.java)
                                .putExtra("EVENT_ID", stage.event.eventId))
                        }.padding(vertical = 8.dp)) {
                            Text("Stage ${stage.event.stageOrder ?: "–"} · ${stage.event.eventName}", style = MaterialTheme.typography.titleSmall)
                            Text("${stageDistance(stage.distanceMeters)} · ${stageDuration(stage.durationMillis)}")
                        }
                        TextButton(enabled = stage.event.eventId != recordingEventId, onClick = { onEdit(stage.event.eventId) }) { Text("Edit") }
                    }
                }
                TextButton(enabled = stages.none { it.event.eventId == recordingEventId }, onClick = { remove = true }) { Text("Ungroup recordings") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (remove) AlertDialog(
        onDismissRequest = { remove = false }, title = { Text("Remove stage group?") },
        text = { Text("All recordings and their data will be kept as individual events.") },
        confirmButton = { TextButton(onClick = {
            remove = false
            scope.launch {
                try { withContext(Dispatchers.IO) { database.stageGroupDao().removeGroup(groupId) } }
                catch (e: Exception) { error = "Could not ungroup recordings. Please try again." }
            }
        }) { Text("Ungroup") } },
        dismissButton = { TextButton(onClick = { remove = false }) { Text("Cancel") } }
    )
}
