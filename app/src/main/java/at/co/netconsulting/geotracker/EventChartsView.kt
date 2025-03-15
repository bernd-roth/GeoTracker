package at.co.netconsulting.geotracker

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import at.co.netconsulting.geotracker.data.SingleEventWithMetric
import at.co.netconsulting.geotracker.domain.FitnessTrackerDatabase
import at.co.netconsulting.geotracker.domain.Location
import at.co.netconsulting.geotracker.domain.Metric
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun EventChartsView(
    record: SingleEventWithMetric,
    onLocationSelected: (Location) -> Unit
) {
    val context = LocalContext.current
    var metrics by remember { mutableStateOf<List<Metric>>(emptyList()) }
    var locationsWithDistance by remember { mutableStateOf<List<Pair<Location, Double>>>(emptyList()) }
    var isLoadingMetrics by remember { mutableStateOf(true) }
    var isLoadingLocations by remember { mutableStateOf(true) }
    var debugInfo by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val database = remember { FitnessTrackerDatabase.getInstance(context) }

    // Load data for this event
    LaunchedEffect(record.eventId) {
        coroutineScope.launch {
            try {
                // Load metrics for speed chart
                val eventMetrics = database.metricDao().getMetricsByEventId(record.eventId)
                metrics = eventMetrics
                isLoadingMetrics = false

                // Load locations for elevation chart
                val locations = database.locationDao().getLocationsForEvent(record.eventId)

                // Calculate distances in memory
                if (locations.isNotEmpty()) {
                    var cumulativeDistance = 0.0
                    val locationsWithDistanceList = mutableListOf<Pair<Location, Double>>()

                    // Add the first location with distance 0
                    locationsWithDistanceList.add(Pair(locations[0], 0.0))

                    for (i in 1 until locations.size) {
                        val prevLocation = locations[i-1]
                        val currentLocation = locations[i]

                        val distance = calculateHaversineDistance(
                            prevLocation.latitude, prevLocation.longitude,
                            currentLocation.latitude, currentLocation.longitude
                        )

                        cumulativeDistance += distance
                        locationsWithDistanceList.add(Pair(currentLocation, cumulativeDistance))
                    }

                    locationsWithDistance = locationsWithDistanceList

                    // Log location info for debugging
                    Log.d("ElevationDebug", "Loaded ${locations.size} locations")
                    if (locations.isNotEmpty()) {
                        Log.d("ElevationDebug", "First location altitude: ${locations.first().altitude} m")
                        Log.d("ElevationDebug", "Last location altitude: ${locations.last().altitude} m")
                        Log.d("ElevationDebug", "Min altitude: ${locations.minOf { it.altitude }} m")
                        Log.d("ElevationDebug", "Max altitude: ${locations.maxOf { it.altitude }} m")
                    }
                }

                isLoadingLocations = false

            } catch (e: Exception) {
                Log.e("EventChartsView", "Error loading data", e)
                isLoadingMetrics = false
                isLoadingLocations = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        if (isLoadingMetrics || isLoadingLocations) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterHorizontally)
            )
        } else {
            // First Chart: Speed over Distance
            if (metrics.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Speed over Distance",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        SpeedDistanceChart(
                            metrics = metrics,
                            context = context,
                            locationsWithDistance = locationsWithDistance,
                            onPointSelected = { distance ->
                                // Find the closest location to the clicked distance
                                val closestLocationPair = locationsWithDistance.minByOrNull {
                                    Math.abs(it.second / 1000 - distance)
                                }

                                if (closestLocationPair != null) {
                                    val (location, distanceMeters) = closestLocationPair
                                    Log.d("Chart", "Selected distance: ${distance}km, Found location at: ${distanceMeters/1000}km")
                                    // Pass the location to the callback
                                    onLocationSelected(location)
                                } else {
                                    Log.e("Chart", "Could not find location for distance: ${distance}km")
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Second Chart: Elevation Profile
            if (locationsWithDistance.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Elevation Profile",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ElevationProfileChart(
                            locationsWithDistance = locationsWithDistance,
                            context = context,
                            onPointSelected = { distance ->
                                // Find the closest location to the clicked distance
                                val closestLocationPair = locationsWithDistance.minByOrNull {
                                    Math.abs(it.second / 1000 - distance)
                                }

                                if (closestLocationPair != null) {
                                    val (location, distanceMeters) = closestLocationPair
                                    Log.d("Chart", "Selected distance: ${distance}km, Found location at: ${distanceMeters/1000}km")
                                    // Pass the location to the callback
                                    onLocationSelected(location)
                                } else {
                                    Log.e("Chart", "Could not find location for distance: ${distance}km")
                                }
                            }
                        )

                        // Small debug info
                        Text(
                            text = debugInfo,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 8.sp,
                            color = androidx.compose.ui.graphics.Color.Gray,
                            modifier = Modifier.padding(top = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Elevation Profile",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No elevation data available for this event",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

// Haversine formula to calculate distance between coordinates in meters
private fun calculateHaversineDistance(
    lat1: Double, lon1: Double,
    lat2: Double, lon2: Double
): Double {
    val R = 6371000.0 // Earth radius in meters

    val latDistance = Math.toRadians(lat2 - lat1)
    val lonDistance = Math.toRadians(lon2 - lon1)

    val a = sin(latDistance / 2) * sin(latDistance / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(lonDistance / 2) * sin(lonDistance / 2)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))

    return R * c
}

@Composable
fun SpeedDistanceChart(
    metrics: List<Metric>,
    context: Context,
    locationsWithDistance: List<Pair<Location, Double>>,
    onPointSelected: (Float) -> Unit
) {
    AndroidView(
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setDrawGridBackground(false)
                setDrawBorders(false)
                setScaleEnabled(true)
                setPinchZoom(true)
                legend.textColor = Color.BLACK

                // Enable highlighting for touch events
                isHighlightPerTapEnabled = true
                isHighlightPerDragEnabled = true

                // Configure X axis (Distance)
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textColor = Color.BLACK
                    setDrawGridLines(true)
                    setDrawAxisLine(true)
                    granularity = 0.5f
                    axisLineWidth = 1.5f
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()} km"
                        }
                    }
                }

                // Configure Y axis (Speed)
                axisLeft.apply {
                    textColor = Color.BLACK
                    setDrawGridLines(true)
                    setDrawAxisLine(true)
                    axisLineWidth = 1.5f
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()} km/h"
                        }
                    }
                }

                // Disable right Y axis
                axisRight.isEnabled = false

                // Add value selection listener
                setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                        e?.let {
                            onPointSelected(it.x)
                            // Highlight the selected point
                            highlightValue(h)
                        }
                    }

                    override fun onNothingSelected() {
                        // Optional: Handle when nothing is selected
                    }
                })
            }
        },
        update = { chart ->
            // Sample data for better performance if there are too many points
            val sampleRate = if (metrics.size > 200) metrics.size / 200 else 1

            // Generate entries for the chart
            val entries = mutableListOf<Entry>()

            metrics.filterIndexed { index, _ -> index % sampleRate == 0 }
                .forEachIndexed { _, metric ->
                    // Use distance in kilometers as X value and speed as Y value
                    val distanceKm = (metric.distance / 1000f).toFloat()
                    entries.add(Entry(distanceKm, metric.speed))
                }

            // Create a dataset with the entries
            val dataSet = LineDataSet(entries, "Speed").apply {
                color = Color.BLUE
                lineWidth = 2f
                setDrawCircles(false)
                setDrawValues(false)
                fillAlpha = 110
                fillColor = Color.parseColor("#4D0000FF") // Semi-transparent blue
                setDrawFilled(true)
                mode = LineDataSet.Mode.CUBIC_BEZIER // Smooth curve

                // Highlight settings
                highLightColor = Color.RED
                highlightLineWidth = 1.5f
                setDrawHighlightIndicators(true)
            }

            // Set the data
            chart.data = LineData(dataSet)

            // Format chart
            chart.xAxis.axisMinimum = 0f
            chart.axisLeft.axisMinimum = 0f

            // Refresh the chart
            chart.invalidate()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    )
}

@Composable
fun ElevationProfileChart(
    locationsWithDistance: List<Pair<Location, Double>>,
    context: Context,
    onPointSelected: (Float) -> Unit
) {
    // Add debug state to help troubleshoot
    var debugInfo by remember { mutableStateOf("") }

    AndroidView(
        factory = { ctx ->
            LineChart(ctx).apply {
                description.isEnabled = false
                setTouchEnabled(true)
                setDrawGridBackground(false)
                setDrawBorders(false)
                setScaleEnabled(true)
                setPinchZoom(true)
                legend.textColor = Color.BLACK

                // Enable highlighting for touch events
                isHighlightPerTapEnabled = true
                isHighlightPerDragEnabled = true

                // Configure X axis (Distance)
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textColor = Color.BLACK
                    setDrawGridLines(true)
                    setDrawAxisLine(true)
                    granularity = 0.5f
                    axisLineWidth = 1.5f
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()} km"
                        }
                    }
                }

                // Configure Y axis (Altitude)
                axisLeft.apply {
                    textColor = Color.BLACK
                    setDrawGridLines(true)
                    setDrawAxisLine(true)
                    axisLineWidth = 1.5f
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()} m"
                        }
                    }
                }

                // Disable right Y axis
                axisRight.isEnabled = false

                // Add value selection listener
                setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                        e?.let {
                            onPointSelected(it.x)
                            // Highlight the selected point
                            highlightValue(h)
                        }
                    }

                    override fun onNothingSelected() {
                        // Optional: Handle when nothing is selected
                    }
                })
            }
        },
        update = { chart ->
            try {
                // Sample data for better performance if there are too many points
                val sampleRate = if (locationsWithDistance.size > 200) locationsWithDistance.size / 200 else 1

                // Generate entries for the chart
                val entries = mutableListOf<Entry>()

                // Better filtering and debugging
                val sampledLocations = locationsWithDistance.filterIndexed { index, _ ->
                    index % sampleRate == 0
                }

                if (sampledLocations.isNotEmpty()) {
                    // Collect debug info
                    val minAlt = sampledLocations.minOf { it.first.altitude }
                    val maxAlt = sampledLocations.maxOf { it.first.altitude }
                    debugInfo = "Locations: ${sampledLocations.size}, Alt: $minAlt-$maxAlt m"

                    sampledLocations.forEach { (location, distance) ->
                        val distanceKm = (distance / 1000f).toFloat()
                        val elevation = location.altitude.toFloat()
                        entries.add(Entry(distanceKm, elevation))
                    }

                    // Create a dataset with the entries
                    val dataSet = LineDataSet(entries, "Altitude").apply {
                        color = Color.GREEN
                        lineWidth = 2f
                        setDrawCircles(false)
                        setDrawValues(false)
                        fillAlpha = 110
                        fillColor = Color.parseColor("#4D00FF00") // Semi-transparent green
                        setDrawFilled(true)
                        mode = LineDataSet.Mode.CUBIC_BEZIER // Smooth curve

                        // Highlight settings
                        highLightColor = Color.RED
                        highlightLineWidth = 1.5f
                        setDrawHighlightIndicators(true)
                    }

                    // Set the data
                    chart.data = LineData(dataSet)

                    // Calculate min and max altitude for better scaling
                    val minAltitude = entries.minOfOrNull { it.y }?.minus(5f) ?: 0f
                    val maxAltitude = entries.maxOfOrNull { it.y }?.plus(5f) ?: 100f
                    val range = maxAltitude - minAltitude

                    // Format chart with explicit range and ensure minimum range
                    chart.xAxis.axisMinimum = 0f

                    // If the elevation range is very small, expand it for better visibility
                    if (range < 10f) {
                        val mid = (minAltitude + maxAltitude) / 2
                        chart.axisLeft.axisMinimum = mid - 10f
                        chart.axisLeft.axisMaximum = mid + 10f
                    } else {
                        chart.axisLeft.axisMinimum = minAltitude
                        chart.axisLeft.axisMaximum = maxAltitude
                    }

                    // Add extra spacing at top and bottom
                    chart.axisLeft.spaceTop = 15f
                    chart.axisLeft.spaceBottom = 15f
                } else {
                    // If no valid data, show an empty chart
                    debugInfo = "No valid locations found."
                    chart.clear()
                    chart.setNoDataText("No elevation data available")
                    chart.setNoDataTextColor(Color.BLACK)
                }

                // Refresh the chart
                chart.invalidate()

            } catch (e: Exception) {
                // Capture and display any errors
                Log.e("ElevationChart", "Error creating chart", e)
                debugInfo = "Error: ${e.message}"
                chart.clear()
                chart.setNoDataText("Error creating chart")
                chart.setNoDataTextColor(Color.RED)
                chart.invalidate()
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    )
}