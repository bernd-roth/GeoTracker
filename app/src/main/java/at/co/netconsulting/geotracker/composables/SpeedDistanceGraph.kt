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
fun SpeedDistanceGraph(
    pathPoints: List<PathPoint>,
    onPointClick: (PathPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val chartEntries = remember(pathPoints) {
        android.util.Log.d("SpeedDistanceGraph", "Processing ${pathPoints.size} path points")
        pathPoints.mapIndexed { index: Int, point: PathPoint ->
            val distanceKm = (point.distance / 1000.0).toFloat()
            android.util.Log.d("SpeedDistanceGraph", "Point $index: distance=${point.distance}m (${distanceKm}km), speed=${point.speed}km/h")

            Entry(
                distanceKm, // Distance in km
                point.speed, // Speed in km/h
                point // Store the original point as data
            )
        }.also { entries ->
            android.util.Log.d("SpeedDistanceGraph", "Created ${entries.size} chart entries")
            entries.forEach { entry ->
                android.util.Log.d("SpeedDistanceGraph", "Entry: x=${entry.x}, y=${entry.y}")
            }
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

                // Left Y-axis setup
                axisLeft.apply {
                    textSize = 10f
                    setDrawGridLines(true)
                    granularity = 1.0f
                    isGranularityEnabled = true
                    axisMinimum = 0f
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
            android.util.Log.d("SpeedDistanceGraph", "Updating chart with ${chartEntries.size} entries")

            if (chartEntries.isNotEmpty()) {
                val dataSet = LineDataSet(chartEntries, "Speed (km/h)").apply {
                    color = Color(0xFF2196F3).toArgb()
                    setDrawFilled(true)
                    setFillColor(Color(0xFF2196F3).toArgb())
                    fillAlpha = 50
                    lineWidth = 2f
                    circleRadius = 4f
                    setCircleColor(Color(0xFF2196F3).toArgb())
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

                // Y-axis formatter for speed
                chart.axisLeft.valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return if (value == value.toInt().toFloat()) {
                            "${value.toInt()} km/h"
                        } else {
                            String.format("%.1f km/h", value)
                        }
                    }
                }

                // Configure chart display
                if (chartEntries.size == 1) {
                    // If only one point, set a reasonable range to make it visible
                    chart.xAxis.axisMaximum = chartEntries[0].x + 1f
                    chart.axisLeft.axisMaximum = chartEntries[0].y + 5f
                } else if (chartEntries.size > 1) {
                    // Let the chart auto-fit for multiple points
                    chart.fitScreen()
                }

                chart.invalidate()
                android.util.Log.d("SpeedDistanceGraph", "Chart updated successfully")
            } else {
                android.util.Log.d("SpeedDistanceGraph", "No chart entries to display")
            }
        }
    )
}