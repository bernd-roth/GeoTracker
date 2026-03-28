package at.co.netconsulting.geotracker.composables

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBike
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Motorcycle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Sailing
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.PlannedEvent
import at.co.netconsulting.geotracker.reminder.ReminderManager
import at.co.netconsulting.geotracker.service.PlannedEventsNetworkManager
import at.co.netconsulting.geotracker.tools.AlarmPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Gold/amber color for competitions
private val CompetitionColor = Color(0xFFFF8F00)

// Diamond shape to distinguish competition dots from activity dots
private val CompetitionShape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(size.width / 2f, size.height)
            lineTo(0f, size.height / 2f)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun CalendarScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { FitnessTrackerDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    val reminderManager = remember { ReminderManager(context) }
    val networkManager = remember { PlannedEventsNetworkManager(context) }

    val currentUserId = remember {
        context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
            .getInt("current_user_id", 1)
    }

    var selectedYear by remember { mutableIntStateOf(LocalDate.now().year) }
    var isLoading by remember { mutableStateOf(true) }
    var eventsByDate by remember { mutableStateOf<Map<String, List<Event>>>(emptyMap()) }
    var competitionsByDate by remember { mutableStateOf<Map<String, List<PlannedEvent>>>(emptyMap()) }
    var selectedDate by remember { mutableStateOf<String?>(null) }

    // Competition form dialog state
    var showCompetitionForm by remember { mutableStateOf(false) }
    var editingCompetition by remember { mutableStateOf<PlannedEvent?>(null) }
    var formDate by remember { mutableStateOf("") }

    // Sync dialog state
    var showSyncDialog by remember { mutableStateOf(false) }

    fun refreshCompetitions() {
        coroutineScope.launch {
            val competitions = withContext(Dispatchers.IO) {
                database.plannedEventDao().getPlannedEventsForYear(currentUserId, selectedYear.toString())
            }
            competitionsByDate = competitions.groupBy { it.plannedEventDate }
        }
    }

    fun deleteCompetition(competition: PlannedEvent) {
        coroutineScope.launch {
            withContext(Dispatchers.IO) {
                reminderManager.cancelReminder(competition.plannedEventId)
                database.plannedEventDao().deletePlannedEvent(competition)
            }
            refreshCompetitions()
        }
    }

    // Load events and competitions when year changes
    LaunchedEffect(selectedYear) {
        isLoading = true
        coroutineScope.launch {
            val events = withContext(Dispatchers.IO) {
                database.eventDao().getEventsForYear(selectedYear.toString())
            }
            val competitions = withContext(Dispatchers.IO) {
                database.plannedEventDao().getPlannedEventsForYear(currentUserId, selectedYear.toString())
            }
            eventsByDate = events.groupBy { it.eventDate }
            competitionsByDate = competitions.groupBy { it.plannedEventDate }
            isLoading = false
        }
    }

    // Competition form dialog
    if (showCompetitionForm) {
        CompetitionFormDialog(
            initialDate = formDate,
            competition = editingCompetition,
            userId = currentUserId,
            database = database,
            reminderManager = reminderManager,
            onDismiss = {
                showCompetitionForm = false
                editingCompetition = null
            },
            onSaved = {
                showCompetitionForm = false
                editingCompetition = null
                refreshCompetitions()
            }
        )
    }

    // Sync dialog
    if (showSyncDialog) {
        SyncDialog(
            networkManager = networkManager,
            onDismiss = { showSyncDialog = false },
            onDownloaded = { refreshCompetitions() }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // Back button row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Back")
            }
            Text(
                text = "Training Calendar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showSyncDialog = true }) {
                Icon(
                    Icons.Default.CloudUpload,
                    contentDescription = "Sync Competitions",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Year selector header
        YearSelector(
            year = selectedYear,
            onPreviousYear = { selectedYear-- },
            onNextYear = { selectedYear++ }
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Year summary
            YearSummary(eventsByDate, competitionsByDate)

            Spacer(modifier = Modifier.height(4.dp))

            // Month grid
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(Month.entries.toList()) { month ->
                    MonthCard(
                        year = selectedYear,
                        month = month,
                        eventsByDate = eventsByDate,
                        competitionsByDate = competitionsByDate,
                        selectedDate = selectedDate,
                        onDateSelected = { date ->
                            selectedDate = if (selectedDate == date) null else date
                        },
                        onAddCompetition = { date ->
                            formDate = date
                            editingCompetition = null
                            showCompetitionForm = true
                        },
                        onEditCompetition = { competition ->
                            formDate = competition.plannedEventDate
                            editingCompetition = competition
                            showCompetitionForm = true
                        },
                        onDeleteCompetition = { competition ->
                            deleteCompetition(competition)
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun YearSelector(
    year: Int,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreviousYear) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous year")
        }
        Text(
            text = year.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        IconButton(onClick = onNextYear) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next year")
        }
    }
}

@Composable
private fun YearSummary(
    eventsByDate: Map<String, List<Event>>,
    competitionsByDate: Map<String, List<PlannedEvent>>
) {
    val allEvents = eventsByDate.values.flatten()
    val totalActivities = allEvents.size
    val activeDays = eventsByDate.keys.size
    val totalCompetitions = competitionsByDate.values.sumOf { it.size }
    val sportCounts = allEvents.groupBy { getSportCategory(it.artOfSport) }
        .mapValues { it.value.size }
        .entries
        .sortedByDescending { it.value }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = totalActivities.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Activities",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = activeDays.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Active Days",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = totalCompetitions.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = CompetitionColor
                    )
                    Text(
                        text = "Competitions",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = sportCounts.size.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sport Types",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (sportCounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sportCounts.take(5).forEach { (sport, count) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        ) {
                            Icon(
                                imageVector = getSportIconForCalendar(sport),
                                contentDescription = sport,
                                modifier = Modifier.size(16.dp),
                                tint = getSportColor(sport)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCard(
    year: Int,
    month: Month,
    eventsByDate: Map<String, List<Event>>,
    competitionsByDate: Map<String, List<PlannedEvent>>,
    selectedDate: String?,
    onDateSelected: (String) -> Unit,
    onAddCompetition: (String) -> Unit,
    onEditCompetition: (PlannedEvent) -> Unit,
    onDeleteCompetition: (PlannedEvent) -> Unit
) {
    val yearMonth = YearMonth.of(year, month)
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek
    // Monday = 0, Sunday = 6
    val startOffset = (firstDayOfWeek.value - DayOfWeek.MONDAY.value)

    // Count activities and competitions in this month
    val monthPrefix = String.format("%d-%02d", year, month.value)
    val monthEvents = eventsByDate.filter { it.key.startsWith(monthPrefix) }
    val monthCompetitions = competitionsByDate.filter { it.key.startsWith(monthPrefix) }
    val monthActivityCount = monthEvents.values.sumOf { it.size }
    val monthCompetitionCount = monthCompetitions.values.sumOf { it.size }

    val today = LocalDate.now()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Month header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    if (monthActivityCount > 0) {
                        Text(
                            text = "$monthActivityCount activities",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (monthCompetitionCount > 0) {
                        if (monthActivityCount > 0) {
                            Text(
                                text = " | ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "$monthCompetitionCount competitions",
                            style = MaterialTheme.typography.bodySmall,
                            color = CompetitionColor
                        )
                    }
                }
            }

            // Day of week headers
            Row(modifier = Modifier.fillMaxWidth()) {
                val dayNames = listOf("M", "T", "W", "T", "F", "S", "S")
                dayNames.forEach { day ->
                    Text(
                        text = day,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Calendar grid
            val totalCells = startOffset + daysInMonth
            val rows = (totalCells + 6) / 7

            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        val day = cellIndex - startOffset + 1

                        if (day in 1..daysInMonth) {
                            val dateStr = String.format("%d-%02d-%02d", year, month.value, day)
                            val dayEvents = eventsByDate[dateStr]
                            val dayCompetitions = competitionsByDate[dateStr]
                            val isToday = LocalDate.of(year, month, day) == today
                            val isSelected = dateStr == selectedDate

                            DayCell(
                                day = day,
                                events = dayEvents,
                                competitions = dayCompetitions,
                                isToday = isToday,
                                isSelected = isSelected,
                                onClick = { onDateSelected(dateStr) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            // Empty cell
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }

            // Show details for selected date in this month
            if (selectedDate != null && selectedDate.startsWith(monthPrefix)) {
                Spacer(modifier = Modifier.height(4.dp))
                DayDetails(
                    date = selectedDate,
                    events = eventsByDate[selectedDate],
                    competitions = competitionsByDate[selectedDate],
                    onAddCompetition = { onAddCompetition(selectedDate) },
                    onEditCompetition = onEditCompetition,
                    onDeleteCompetition = onDeleteCompetition
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    events: List<Event>?,
    competitions: List<PlannedEvent>?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasEvents = events != null && events.isNotEmpty()
    val hasCompetitions = competitions != null && competitions.isNotEmpty()
    val hasContent = hasEvents || hasCompetitions

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .clip(RoundedCornerShape(4.dp))
            .then(
                if (isSelected) Modifier.border(
                    2.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(4.dp)
                )
                else Modifier
            )
            .then(
                when {
                    hasEvents -> {
                        val sportColor = getSportColor(
                            getSportCategory(events!!.first().artOfSport)
                        )
                        Modifier.background(sportColor.copy(alpha = 0.2f))
                    }
                    hasCompetitions -> {
                        Modifier.background(CompetitionColor.copy(alpha = 0.15f))
                    }
                    else -> Modifier
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 10.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
            )

            if (hasContent) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasEvents) {
                        val uniqueSports = events!!.map { getSportCategory(it.artOfSport) }.distinct()
                        uniqueSports.take(2).forEach { sport ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .padding(0.5.dp)
                                    .clip(CircleShape)
                                    .background(getSportColor(sport))
                            )
                        }
                    }
                    if (hasCompetitions) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .padding(0.5.dp)
                                .clip(CompetitionShape)
                                .background(CompetitionColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetails(
    date: String,
    events: List<Event>?,
    competitions: List<PlannedEvent>?,
    onAddCompetition: () -> Unit,
    onEditCompetition: (PlannedEvent) -> Unit,
    onDeleteCompetition: (PlannedEvent) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header row with date and add button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = onAddCompetition,
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Competition",
                        modifier = Modifier.size(16.dp),
                        tint = CompetitionColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Competition",
                        style = MaterialTheme.typography.labelSmall,
                        color = CompetitionColor
                    )
                }
            }

            // Show competitions
            competitions?.forEach { competition ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Competition",
                        modifier = Modifier.size(18.dp),
                        tint = CompetitionColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = competition.plannedEventName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val locationParts = listOfNotNull(
                            competition.plannedEventCity.ifBlank { null },
                            competition.plannedEventCountry.ifBlank { null }
                        )
                        val details = buildString {
                            if (competition.plannedEventType.isNotBlank()) append(competition.plannedEventType)
                            if (locationParts.isNotEmpty()) {
                                if (isNotEmpty()) append(" - ")
                                append(locationParts.joinToString(", "))
                            }
                        }
                        if (details.isNotBlank()) {
                            Text(
                                text = details,
                                style = MaterialTheme.typography.labelSmall,
                                color = CompetitionColor.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (competition.isEnteredAndFinished) {
                            Text(
                                text = "Entered and Finished",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(
                        onClick = { onEditCompetition(competition) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = { onDeleteCompetition(competition) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Show activities
            events?.forEach { event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = getSportIconForCalendar(event.artOfSport),
                        contentDescription = event.artOfSport,
                        modifier = Modifier.size(18.dp),
                        tint = getSportColor(getSportCategory(event.artOfSport))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = event.eventName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = event.artOfSport,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ─── Competition Form Dialog ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompetitionFormDialog(
    initialDate: String,
    competition: PlannedEvent?,
    userId: Int,
    database: FitnessTrackerDatabase,
    reminderManager: ReminderManager,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEditing = competition != null

    // Form state
    var name by remember { mutableStateOf(competition?.plannedEventName ?: "") }
    var date by remember { mutableStateOf(competition?.plannedEventDate ?: initialDate) }
    var country by remember { mutableStateOf(competition?.plannedEventCountry ?: "") }
    var city by remember { mutableStateOf(competition?.plannedEventCity ?: "") }
    var type by remember { mutableStateOf(competition?.plannedEventType ?: "") }
    var website by remember { mutableStateOf(competition?.website ?: "") }
    var comment by remember { mutableStateOf(competition?.comment ?: "") }
    var isEnteredAndFinished by remember { mutableStateOf(competition?.isEnteredAndFinished ?: false) }

    // Reminder state
    var reminderDateTime by remember { mutableStateOf(competition?.reminderDateTime ?: "") }
    var isReminderActive by remember { mutableStateOf(competition?.isReminderActive ?: false) }
    var isRecurring by remember { mutableStateOf(competition?.isRecurring ?: false) }
    var recurringType by remember { mutableStateOf(competition?.recurringType?.ifEmpty { "daily" } ?: "daily") }
    var recurringInterval by remember { mutableIntStateOf(competition?.recurringInterval ?: 1) }
    var recurringEndDate by remember { mutableStateOf(competition?.recurringEndDate ?: "") }
    var selectedDaysOfWeek by remember {
        mutableStateOf(
            if (competition?.recurringType == "weekly" && !competition.recurringDaysOfWeek.isNullOrEmpty()) {
                competition.recurringDaysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            } else emptySet()
        )
    }

    // Display state for date/time pickers
    var reminderDateFormatted by remember {
        mutableStateOf(
            competition?.reminderDateTime?.let { dt ->
                if (dt.isNotEmpty()) {
                    reminderManager.parseReminderDateTimeToCalendar(dt)?.let { cal ->
                        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                    } ?: ""
                } else ""
            } ?: ""
        )
    }
    var reminderTimeFormatted by remember {
        mutableStateOf(
            competition?.reminderDateTime?.let { dt ->
                if (dt.isNotEmpty()) {
                    reminderManager.parseReminderDateTimeToCalendar(dt)?.let { cal ->
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time)
                    } ?: ""
                } else ""
            } ?: ""
        )
    }
    var recurringEndDateFormatted by remember {
        mutableStateOf(
            competition?.recurringEndDate?.let { ed ->
                if (ed.isNotEmpty()) {
                    try {
                        val cal = Calendar.getInstance().apply {
                            time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(ed) ?: Date()
                        }
                        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(cal.time)
                    } catch (_: Exception) { "" }
                } else ""
            } ?: ""
        )
    }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun save() {
        scope.launch {
            isSaving = true
            errorMessage = null
            try {
                val plannedEvent = if (isEditing) {
                    competition!!.copy(
                        plannedEventName = name,
                        plannedEventDate = date,
                        plannedEventCountry = country,
                        plannedEventCity = city,
                        plannedEventType = type,
                        website = website,
                        comment = comment,
                        isEnteredAndFinished = isEnteredAndFinished,
                        reminderDateTime = reminderDateTime,
                        isReminderActive = isReminderActive,
                        isRecurring = isRecurring,
                        recurringType = recurringType,
                        recurringInterval = recurringInterval,
                        recurringEndDate = recurringEndDate,
                        recurringDaysOfWeek = if (recurringType == "weekly") selectedDaysOfWeek.joinToString(",") else ""
                    )
                } else {
                    PlannedEvent(
                        userId = userId,
                        plannedEventName = name,
                        plannedEventDate = date,
                        plannedEventCountry = country,
                        plannedEventCity = city,
                        plannedEventType = type,
                        website = website,
                        comment = comment,
                        isEnteredAndFinished = isEnteredAndFinished,
                        reminderDateTime = reminderDateTime,
                        isReminderActive = isReminderActive,
                        isRecurring = isRecurring,
                        recurringType = recurringType,
                        recurringInterval = recurringInterval,
                        recurringEndDate = recurringEndDate,
                        recurringDaysOfWeek = if (recurringType == "weekly") selectedDaysOfWeek.joinToString(",") else "",
                        plannedLatitude = null,
                        plannedLongitude = null
                    )
                }

                val savedEventId = withContext(Dispatchers.IO) {
                    if (isEditing) {
                        database.plannedEventDao().updatePlannedEvent(plannedEvent)
                        plannedEvent.plannedEventId
                    } else {
                        database.plannedEventDao().insertPlannedEvent(plannedEvent).toInt()
                    }
                }

                // Handle reminder scheduling
                if (isReminderActive && reminderDateTime.isNotEmpty()) {
                    try {
                        if (AlarmPermissionHelper.checkExactAlarmPermission(context)) {
                            val savedEvent = plannedEvent.copy(plannedEventId = savedEventId)
                            reminderManager.updateReminder(savedEvent)
                        } else {
                            AlarmPermissionHelper.requestExactAlarmPermission(context as ComponentActivity) { hasPermission ->
                                if (hasPermission) {
                                    val savedEvent = plannedEvent.copy(plannedEventId = savedEventId)
                                    reminderManager.updateReminder(savedEvent)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("CalendarScreen", "Error setting up reminder", e)
                    }
                } else if (isEditing) {
                    try {
                        reminderManager.cancelReminder(savedEventId)
                    } catch (e: Exception) {
                        Log.e("CalendarScreen", "Error cancelling reminder", e)
                    }
                }

                onSaved()
            } catch (e: Exception) {
                Log.e("CalendarScreen", "Error saving competition", e)
                errorMessage = "Failed to save: ${e.message}"
            } finally {
                isSaving = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Title
                Text(
                    text = if (isEditing) "Edit Competition" else "Add Competition",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Competition Name *") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true
                )

                // Date
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD) *") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    placeholder = { Text("2024-12-25") }
                )

                // Country / City
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { Text("Country *") },
                        modifier = Modifier.weight(1f).padding(end = 4.dp, bottom = 8.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = city,
                        onValueChange = { city = it },
                        label = { Text("City *") },
                        modifier = Modifier.weight(1f).padding(start = 4.dp, bottom = 8.dp),
                        singleLine = true
                    )
                }

                // Type
                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Competition Type") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    placeholder = { Text("Marathon, Triathlon, etc.") }
                )

                // Website
                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Website") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    singleLine = true,
                    placeholder = { Text("https://...") }
                )

                // Comment
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    maxLines = 3
                )

                // Entered and finished
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isEnteredAndFinished,
                        onCheckedChange = { isEnteredAndFinished = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Competition entered and finished")
                }

                // ── Reminder Section ──
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isReminderActive,
                        onCheckedChange = { isReminderActive = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Set reminder for this competition",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                AnimatedVisibility(visible = isReminderActive) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Reminder date picker
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 4.dp)
                                    .clickable {
                                        val calendar = Calendar.getInstance()
                                        if (reminderDateTime.isNotEmpty()) {
                                            reminderManager.parseReminderDateTimeToCalendar(reminderDateTime)?.let {
                                                calendar.time = it.time
                                            }
                                        }
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, dayOfMonth ->
                                                val newCal = Calendar.getInstance().apply {
                                                    set(year, month, dayOfMonth)
                                                    if (reminderDateTime.isNotEmpty()) {
                                                        reminderManager.parseReminderDateTimeToCalendar(reminderDateTime)?.let { existing ->
                                                            set(Calendar.HOUR_OF_DAY, existing.get(Calendar.HOUR_OF_DAY))
                                                            set(Calendar.MINUTE, existing.get(Calendar.MINUTE))
                                                        }
                                                    }
                                                }
                                                reminderDateTime = reminderManager.formatReminderDateTime(newCal)
                                                reminderDateFormatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(newCal.time)
                                            },
                                            calendar.get(Calendar.YEAR),
                                            calendar.get(Calendar.MONTH),
                                            calendar.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                            ) {
                                OutlinedTextField(
                                    value = reminderDateFormatted,
                                    onValueChange = {},
                                    label = { Text("Reminder Date") },
                                    modifier = Modifier.fillMaxWidth(),
                                    readOnly = true,
                                    enabled = false,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    trailingIcon = {
                                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                                    }
                                )
                            }

                            // Reminder time picker
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 4.dp)
                                    .clickable {
                                        val calendar = Calendar.getInstance()
                                        if (reminderDateTime.isNotEmpty()) {
                                            reminderManager.parseReminderDateTimeToCalendar(reminderDateTime)?.let {
                                                calendar.time = it.time
                                            }
                                        }
                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                val newCal = Calendar.getInstance().apply {
                                                    if (reminderDateTime.isNotEmpty()) {
                                                        reminderManager.parseReminderDateTimeToCalendar(reminderDateTime)?.let { existing ->
                                                            time = existing.time
                                                        }
                                                    }
                                                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                    set(Calendar.MINUTE, minute)
                                                    set(Calendar.SECOND, 0)
                                                }
                                                reminderDateTime = reminderManager.formatReminderDateTime(newCal)
                                                reminderTimeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(newCal.time)
                                            },
                                            calendar.get(Calendar.HOUR_OF_DAY),
                                            calendar.get(Calendar.MINUTE),
                                            true
                                        ).show()
                                    }
                            ) {
                                OutlinedTextField(
                                    value = reminderTimeFormatted,
                                    onValueChange = {},
                                    label = { Text("Reminder Time") },
                                    modifier = Modifier.fillMaxWidth(),
                                    readOnly = true,
                                    enabled = false,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    trailingIcon = {
                                        Icon(Icons.Default.Schedule, contentDescription = "Select Time")
                                    }
                                )
                            }
                        }

                        if (reminderDateTime.isNotEmpty()) {
                            Text(
                                text = "You will be reminded on $reminderDateFormatted at $reminderTimeFormatted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ── Recurring Options ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isRecurring,
                                onCheckedChange = { isRecurring = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Make this a recurring reminder",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        AnimatedVisibility(visible = isRecurring) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))

                                // Recurring type dropdown
                                var recurringTypeExpanded by remember { mutableStateOf(false) }
                                val recurringTypes = mapOf(
                                    "daily" to "Daily",
                                    "weekly" to "Weekly",
                                    "monthly" to "Monthly",
                                    "yearly" to "Yearly"
                                )

                                ExposedDropdownMenuBox(
                                    expanded = recurringTypeExpanded,
                                    onExpandedChange = { recurringTypeExpanded = !recurringTypeExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = recurringTypes[recurringType] ?: "Daily",
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Repeat") },
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = recurringTypeExpanded)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor()
                                            .padding(bottom = 8.dp)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = recurringTypeExpanded,
                                        onDismissRequest = { recurringTypeExpanded = false }
                                    ) {
                                        recurringTypes.forEach { (key, value) ->
                                            DropdownMenuItem(
                                                text = { Text(value) },
                                                onClick = {
                                                    recurringType = key
                                                    recurringTypeExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Interval selection
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Every", modifier = Modifier.padding(end = 8.dp))
                                    OutlinedTextField(
                                        value = recurringInterval.toString(),
                                        onValueChange = { value ->
                                            value.toIntOrNull()?.let {
                                                if (it > 0) recurringInterval = it
                                            }
                                        },
                                        modifier = Modifier.width(80.dp),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                    Text(
                                        when (recurringType) {
                                            "daily" -> if (recurringInterval == 1) "day" else "days"
                                            "weekly" -> if (recurringInterval == 1) "week" else "weeks"
                                            "monthly" -> if (recurringInterval == 1) "month" else "months"
                                            "yearly" -> if (recurringInterval == 1) "year" else "years"
                                            else -> "days"
                                        },
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }

                                // Days of week selection for weekly recurring
                                AnimatedVisibility(visible = recurringType == "weekly") {
                                    CalendarDaysOfWeekSelector(
                                        selectedDaysOfWeek = selectedDaysOfWeek,
                                        onDaysChanged = { selectedDaysOfWeek = it }
                                    )
                                }

                                // End date selection
                                Text(
                                    "End date (optional):",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val calendar = Calendar.getInstance()
                                            DatePickerDialog(
                                                context,
                                                { _, year, month, dayOfMonth ->
                                                    val selectedCalendar = Calendar.getInstance().apply {
                                                        set(year, month, dayOfMonth)
                                                    }
                                                    recurringEndDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedCalendar.time)
                                                    recurringEndDateFormatted = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(selectedCalendar.time)
                                                },
                                                calendar.get(Calendar.YEAR),
                                                calendar.get(Calendar.MONTH),
                                                calendar.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        }
                                        .padding(bottom = 8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = recurringEndDateFormatted,
                                        onValueChange = {},
                                        label = { Text("End Date") },
                                        placeholder = { Text("Never") },
                                        modifier = Modifier.fillMaxWidth(),
                                        readOnly = true,
                                        enabled = false,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        trailingIcon = {
                                            Row {
                                                if (recurringEndDate.isNotEmpty()) {
                                                    IconButton(
                                                        onClick = {
                                                            recurringEndDate = ""
                                                            recurringEndDateFormatted = ""
                                                        }
                                                    ) {
                                                        Icon(Icons.Default.Close, contentDescription = "Clear end date")
                                                    }
                                                }
                                                Icon(Icons.Default.DateRange, contentDescription = "Select end date")
                                            }
                                        }
                                    )
                                }

                                // Recurring summary
                                if (isRecurring) {
                                    val summaryText = calendarBuildRecurringSummary(
                                        recurringType,
                                        recurringInterval,
                                        selectedDaysOfWeek,
                                        recurringEndDateFormatted
                                    )
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ) {
                                        Text(
                                            text = summaryText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { save() },
                        enabled = name.isNotBlank() && date.isNotBlank() &&
                                country.isNotBlank() && city.isNotBlank() && !isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(if (isEditing) "Update" else "Save")
                        }
                    }
                }
            }
        }
    }
}

// ─── Sync Dialog ────────────────────────────────────────────────────────────────

@Composable
private fun SyncDialog(
    networkManager: PlannedEventsNetworkManager,
    onDismiss: () -> Unit,
    onDownloaded: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var syncSuccess by remember { mutableStateOf<Boolean?>(null) }

    fun uploadEvents() {
        scope.launch {
            isSyncing = true
            syncMessage = "Uploading events to server..."
            syncSuccess = null
            try {
                val result = networkManager.uploadPlannedEvents()
                if (result.success) {
                    syncSuccess = true
                    syncMessage = buildString {
                        append("Upload successful! ")
                        if (result.uploadedCount > 0) append("${result.uploadedCount} uploaded, ")
                        if (result.duplicateCount > 0) append("${result.duplicateCount} duplicates skipped, ")
                        if (result.errorCount > 0) append("${result.errorCount} errors")
                    }
                } else {
                    syncSuccess = false
                    syncMessage = "Upload failed: ${result.message}"
                }
            } catch (e: Exception) {
                Log.e("CalendarScreen", "Error in uploadEvents", e)
                syncSuccess = false
                syncMessage = "Upload error: ${e.message}"
            } finally {
                isSyncing = false
            }
        }
    }

    fun downloadEvents() {
        scope.launch {
            isSyncing = true
            syncMessage = "Downloading events from server..."
            syncSuccess = null
            try {
                val result = networkManager.downloadPlannedEvents()
                if (result.success) {
                    syncSuccess = true
                    syncMessage = buildString {
                        append("Download successful! ")
                        if (result.downloadedCount > 0) append("${result.downloadedCount} new events added, ")
                        if (result.duplicateCount > 0) append("${result.duplicateCount} duplicates skipped")
                    }
                    onDownloaded()
                } else {
                    syncSuccess = false
                    syncMessage = "Download failed: ${result.message}"
                }
            } catch (e: Exception) {
                Log.e("CalendarScreen", "Error in downloadEvents", e)
                syncSuccess = false
                syncMessage = "Download error: ${e.message}"
            } finally {
                isSyncing = false
            }
        }
    }

    fun testConnection() {
        scope.launch {
            isSyncing = true
            syncMessage = "Testing connection..."
            syncSuccess = null
            try {
                val result = networkManager.testPlannedEventsConnection()
                syncSuccess = result.success
                syncMessage = if (result.success) {
                    "Connection test successful!"
                } else {
                    "Connection test failed: ${result.message}"
                }
            } catch (e: Exception) {
                Log.e("CalendarScreen", "Error in connection test", e)
                syncSuccess = false
                syncMessage = "Connection test error: ${e.message}"
            } finally {
                isSyncing = false
            }
        }
    }

    Dialog(onDismissRequest = { if (!isSyncing) onDismiss() }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sync with Server",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Share your events with others and discover new competitions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Status message
                syncMessage?.let { message ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (syncSuccess) {
                                true -> MaterialTheme.colorScheme.primaryContainer
                                false -> MaterialTheme.colorScheme.errorContainer
                                null -> MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(12.dp),
                            color = when (syncSuccess) {
                                true -> MaterialTheme.colorScheme.onPrimaryContainer
                                false -> MaterialTheme.colorScheme.onErrorContainer
                                null -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Sync buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { uploadEvents() },
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Upload")
                        }
                    }

                    OutlinedButton(
                        onClick = { downloadEvents() },
                        enabled = !isSyncing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Test connection button
                OutlinedButton(
                    onClick = { testConnection() },
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Connection")
                }

                // Help text
                Text(
                    text = "Upload: Share your events with the community\nDownload: Get new events from other users\nTest: Verify server connection",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Close button
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun CalendarDaysOfWeekSelector(
    selectedDaysOfWeek: Set<Int>,
    onDaysChanged: (Set<Int>) -> Unit
) {
    Column {
        Text(
            "Select days:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        val daysOfWeek = listOf(
            Calendar.MONDAY to "Mon",
            Calendar.TUESDAY to "Tue",
            Calendar.WEDNESDAY to "Wed",
            Calendar.THURSDAY to "Thu",
            Calendar.FRIDAY to "Fri",
            Calendar.SATURDAY to "Sat",
            Calendar.SUNDAY to "Sun"
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(daysOfWeek) { (dayConstant, dayName) ->
                FilterChip(
                    onClick = {
                        onDaysChanged(
                            if (selectedDaysOfWeek.contains(dayConstant)) {
                                selectedDaysOfWeek - dayConstant
                            } else {
                                selectedDaysOfWeek + dayConstant
                            }
                        )
                    },
                    label = { Text(dayName) },
                    selected = selectedDaysOfWeek.contains(dayConstant)
                )
            }
        }
    }
}

private fun calendarBuildRecurringSummary(
    recurringType: String,
    recurringInterval: Int,
    selectedDaysOfWeek: Set<Int>,
    recurringEndDateFormatted: String
): String {
    val intervalText = when (recurringType) {
        "daily" -> if (recurringInterval == 1) "every day" else "every $recurringInterval days"
        "weekly" -> {
            if (selectedDaysOfWeek.isNotEmpty()) {
                val dayNames = selectedDaysOfWeek.sorted().map { dayConstant ->
                    when (dayConstant) {
                        Calendar.MONDAY -> "Mon"
                        Calendar.TUESDAY -> "Tue"
                        Calendar.WEDNESDAY -> "Wed"
                        Calendar.THURSDAY -> "Thu"
                        Calendar.FRIDAY -> "Fri"
                        Calendar.SATURDAY -> "Sat"
                        Calendar.SUNDAY -> "Sun"
                        else -> ""
                    }
                }.filter { it.isNotEmpty() }
                if (recurringInterval == 1) {
                    "every ${dayNames.joinToString(", ")}"
                } else {
                    "every $recurringInterval weeks on ${dayNames.joinToString(", ")}"
                }
            } else {
                if (recurringInterval == 1) "every week" else "every $recurringInterval weeks"
            }
        }
        "monthly" -> if (recurringInterval == 1) "every month" else "every $recurringInterval months"
        "yearly" -> if (recurringInterval == 1) "every year" else "every $recurringInterval years"
        else -> "Unknown"
    }
    val endText = if (recurringEndDateFormatted.isNotEmpty()) {
        " until $recurringEndDateFormatted"
    } else {
        " (no end date)"
    }
    return "Recurring reminder: $intervalText$endText"
}

// ─── Sport Helper Functions ─────────────────────────────────────────────────────

/** Maps a specific sport subcategory to its parent category. */
private fun getSportCategory(artOfSport: String): String {
    return when (artOfSport) {
        "Trail Running", "Ultramarathon", "Marathon", "Road Running",
        "Orienteering", "Backyard Ultra", "Running" -> "Running"

        "Gravel Bike", "E-Bike", "Racing Bicycle", "Mountain Bike", "Cycling" -> "Cycling"

        "Swimming - Open Water", "Kayaking", "Canoeing",
        "Stand Up Paddleboarding", "Water Sports" -> "Water Sports"

        "Nordic Walking", "Urban Walking", "Walking" -> "Walking"

        "Mountain Hiking", "Forest Hiking", "Hiking" -> "Hiking"

        "Car", "Motorcycle", "Motorsport" -> "Motorsport"

        "Ski", "Snowboard", "Cross Country Skiing", "Ski Touring",
        "Biathlon", "Sledding", "Snowshoeing", "Winter Sport" -> "Winter Sport"

        "Duathlon", "Triathlon", "Ultratriathlon", "Multisport Race" -> "Multisport Race"

        "Lactate Threshold (30min TT)", "Fitness Test" -> "Fitness Test"

        else -> artOfSport
    }
}

/** Color for each sport category. */
private fun getSportColor(category: String): Color {
    return when (category) {
        "Running" -> Color(0xFFE64A19)       // Deep Orange
        "Cycling" -> Color(0xFF388E3C)        // Green
        "Water Sports" -> Color(0xFF1976D2)   // Blue
        "Walking" -> Color(0xFF7B1FA2)        // Purple
        "Hiking" -> Color(0xFF5D4037)         // Brown
        "Motorsport" -> Color(0xFF455A64)     // Blue Grey
        "Winter Sport" -> Color(0xFF0097A7)   // Cyan
        "Multisport Race" -> Color(0xFFF57C00) // Orange
        "Fitness Test" -> Color(0xFFC62828)   // Red
        else -> Color(0xFF757575)             // Grey
    }
}

/** Icon for each sport type (specific or category). */
private fun getSportIconForCalendar(sportName: String): ImageVector {
    return when (sportName) {
        "Trail Running" -> Icons.Default.Terrain
        "Ultramarathon" -> Icons.Default.Timer
        "Marathon" -> Icons.Default.Speed
        "Road Running" -> Icons.Default.Route
        "Orienteering" -> Icons.Default.MyLocation
        "Backyard Ultra" -> Icons.Default.Replay
        "Running" -> Icons.Default.DirectionsRun

        "Gravel Bike" -> Icons.Default.Terrain
        "E-Bike" -> Icons.Default.ElectricBike
        "Racing Bicycle" -> Icons.Default.Speed
        "Mountain Bike" -> Icons.Default.Landscape
        "Cycling" -> Icons.Default.DirectionsBike

        "Swimming - Open Water" -> Icons.Default.Waves
        "Kayaking" -> Icons.Default.Sailing
        "Canoeing" -> Icons.Default.Sailing
        "Stand Up Paddleboarding" -> Icons.Default.Sailing
        "Water Sports" -> Icons.Default.Waves

        "Nordic Walking" -> Icons.Default.Hiking
        "Urban Walking" -> Icons.Default.LocationCity
        "Walking" -> Icons.Default.DirectionsWalk

        "Mountain Hiking" -> Icons.Default.Landscape
        "Forest Hiking" -> Icons.Default.Forest
        "Hiking" -> Icons.Default.Hiking

        "Car" -> Icons.Default.DirectionsCar
        "Motorcycle" -> Icons.Default.Motorcycle
        "Motorsport" -> Icons.Default.SportsMotorsports

        "Ski" -> Icons.Default.Terrain
        "Snowboard" -> Icons.Default.Landscape
        "Cross Country Skiing" -> Icons.Default.DirectionsRun
        "Ski Touring" -> Icons.Default.Terrain
        "Biathlon" -> Icons.Default.GpsFixed
        "Sledding" -> Icons.Default.Speed
        "Snowshoeing" -> Icons.Default.Hiking
        "Winter Sport" -> Icons.Default.Landscape

        "Duathlon" -> Icons.Default.DirectionsRun
        "Triathlon" -> Icons.Default.Waves
        "Ultratriathlon" -> Icons.Default.Timer
        "Multisport Race" -> Icons.Default.FitnessCenter

        "Lactate Threshold (30min TT)" -> Icons.Default.MonitorHeart
        "Fitness Test" -> Icons.Default.FitnessCenter

        else -> Icons.Default.DirectionsRun
    }
}
