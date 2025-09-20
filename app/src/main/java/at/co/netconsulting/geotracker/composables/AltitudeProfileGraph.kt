package at.co.netconsulting.geotracker.composables

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import at.co.netconsulting.geotracker.data.PathPoint
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.highlight.Highlight

@Composable
fun AltitudeProfileGraph(
    pathPoints: List<PathPoint>,
    onPointClick: (PathPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val chartEntries = remember(pathPoints) {
        android.util.Log.d("AltitudeProfileGraph", "Processing ${pathPoints.size} path points")
        pathPoints.mapIndexed { _: Int, point: PathPoint ->
            val distanceKm = (point.distance / 1000.0).toFloat()
            android.util.Log.d("AltitudeProfileGraph", "Point: distance=${point.distance}m (${distanceKm}km), altitude=${point.altitude}m")

            Entry(
                distanceKm, // Distance in km
                point.altitude.toFloat(), // Altitude in meters
                point // Store the original point as data
            )
        }.also { entries ->
            android.util.Log.d("AltitudeProfileGraph", "Created ${entries.size} altitude chart entries")
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            LineChart(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                description.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)

                // X-axis setup
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textSize = 10f
                    setDrawGridLines(true)
                    granularity = 0.1f
                    labelCount = 8
                    isGranularityEnabled = true
                    axisMinimum = 0f
                    setLabelCount(6, false)
                }

                // Left Y-axis setup (altitude in meters)
                axisLeft.apply {
                    textSize = 10f
                    setDrawGridLines(true)
                    granularity = 10.0f // 10 meter intervals
                    isGranularityEnabled = true
                    setLabelCount(6, false)
                }

                // Right Y-axis setup (disabled)
                axisRight.isEnabled = false

                // Legend setup
                legend.apply {
                    textSize = 12f
                    verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP
                    horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.RIGHT
                    orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                }

                // Set up click listener
                setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                    override fun onValueSelected(e: Entry?, h: Highlight?) {
                        e?.data?.let { data ->
                            if (data is PathPoint) {
                                onPointClick(data)
                            }
                        }
                    }

                    override fun onNothingSelected() {
                        // Handle when nothing is selected if needed
                    }
                })
            }
        },
        update = { chart ->
            android.util.Log.d("AltitudeProfileGraph", "Updating chart with ${chartEntries.size} entries")

            if (chartEntries.isNotEmpty()) {
                val dataSet = LineDataSet(chartEntries, "Altitude (m)").apply {
                    color = Color(0xFF4CAF50).toArgb() // Green color for altitude
                    setDrawFilled(true)
                    setFillColor(Color(0xFF4CAF50).toArgb())
                    fillAlpha = 50
                    lineWidth = 2f
                    circleRadius = 4f
                    setCircleColor(Color(0xFF4CAF50).toArgb())
                    setDrawCircleHole(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    setDrawValues(false)
                    isHighlightEnabled = true
                    highlightLineWidth = 2f
                    setDrawHighlightIndicators(true)
                }

                val lineData = LineData(dataSet)
                chart.data = lineData

                // X-axis formatter for distance
                chart.xAxis.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value == value.toInt().toFloat()) {
                            "${value.toInt()} km"
                        } else {
                            String.format("%.1f km", value)
                        }
                    }
                }

                // Y-axis formatter for altitude
                chart.axisLeft.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value == value.toInt().toFloat()) {
                            "${value.toInt()} m"
                        } else {
                            String.format("%.1f m", value)
                        }
                    }
                }

                // Configure chart display
                if (chartEntries.size == 1) {
                    // If only one point, set a reasonable range to make it visible
                    chart.xAxis.axisMaximum = chartEntries[0].x + 1f
                    chart.axisLeft.axisMaximum = chartEntries[0].y + 50f // 50m buffer above
                    chart.axisLeft.axisMinimum = (chartEntries[0].y - 50f).coerceAtLeast(0f) // 50m buffer below, but not negative
                } else if (chartEntries.size > 1) {
                    // For multiple points, add some padding to Y-axis
                    val minAltitude = chartEntries.minOf { it.y }
                    val maxAltitude = chartEntries.maxOf { it.y }
                    val altitudeRange = maxAltitude - minAltitude
                    val padding = (altitudeRange * 0.1f).coerceAtLeast(20f) // 10% padding, minimum 20m

                    chart.axisLeft.axisMinimum = (minAltitude - padding).coerceAtLeast(0f)
                    chart.axisLeft.axisMaximum = maxAltitude + padding
                    chart.fitScreen()
                }

                chart.invalidate()
                android.util.Log.d("AltitudeProfileGraph", "Altitude chart updated successfully")
            } else {
                android.util.Log.d("AltitudeProfileGraph", "No chart entries to display")
            }
        }
    )
}