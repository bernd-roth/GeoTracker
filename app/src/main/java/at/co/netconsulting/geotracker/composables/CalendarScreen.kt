package at.co.netconsulting.geotracker.composables

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ElectricBike
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
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.co.netconsulting.geotracker.domain.Event
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val database = remember { FitnessTrackerDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    var selectedYear by remember { mutableIntStateOf(LocalDate.now().year) }
    var isLoading by remember { mutableStateOf(true) }
    // Map of "YYYY-MM-DD" -> list of events on that day
    var eventsByDate by remember { mutableStateOf<Map<String, List<Event>>>(emptyMap()) }
    // Selected day to show details
    var selectedDate by remember { mutableStateOf<String?>(null) }

    // Load events when year changes
    LaunchedEffect(selectedYear) {
        isLoading = true
        coroutineScope.launch {
            val events = withContext(Dispatchers.IO) {
                database.eventDao().getEventsForYear(selectedYear.toString())
            }
            eventsByDate = events.groupBy { it.eventDate }
            isLoading = false
        }
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
                fontWeight = FontWeight.Bold
            )
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
            YearSummary(eventsByDate)

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
                        selectedDate = selectedDate,
                        onDateSelected = { date ->
                            selectedDate = if (selectedDate == date) null else date
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
private fun YearSummary(eventsByDate: Map<String, List<Event>>) {
    val allEvents = eventsByDate.values.flatten()
    val totalActivities = allEvents.size
    val activeDays = eventsByDate.keys.size
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
    selectedDate: String?,
    onDateSelected: (String) -> Unit
) {
    val yearMonth = YearMonth.of(year, month)
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek
    // Monday = 0, Sunday = 6
    val startOffset = (firstDayOfWeek.value - DayOfWeek.MONDAY.value)

    // Count activities in this month
    val monthPrefix = String.format("%d-%02d", year, month.value)
    val monthEvents = eventsByDate.filter { it.key.startsWith(monthPrefix) }
    val monthActivityCount = monthEvents.values.sumOf { it.size }

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
                if (monthActivityCount > 0) {
                    Text(
                        text = "$monthActivityCount activities",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            val isToday = LocalDate.of(year, month, day) == today
                            val isSelected = dateStr == selectedDate

                            DayCell(
                                day = day,
                                events = dayEvents,
                                isToday = isToday,
                                isSelected = isSelected,
                                onClick = {
                                    if (dayEvents != null) {
                                        onDateSelected(dateStr)
                                    }
                                },
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
                val selectedEvents = eventsByDate[selectedDate]
                if (selectedEvents != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    DayDetails(date = selectedDate, events = selectedEvents)
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    events: List<Event>?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasEvents = events != null && events.isNotEmpty()

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
                    else -> Modifier
                }
            )
            .clickable(enabled = hasEvents) { onClick() },
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

            if (hasEvents) {
                // Show sport dots/icons
                val uniqueSports = events!!.map { getSportCategory(it.artOfSport) }.distinct()
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    uniqueSports.take(3).forEach { sport ->
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .padding(0.5.dp)
                                .clip(CircleShape)
                                .background(getSportColor(sport))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetails(date: String, events: List<Event>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            events.forEach { event ->
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
