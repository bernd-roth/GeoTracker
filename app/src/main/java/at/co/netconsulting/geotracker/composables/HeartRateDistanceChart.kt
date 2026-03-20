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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * A composable that displays heart rate data in relation to distance covered
 */
@Composable
fun HeartRateDistanceChart(
    entries: List<Entry>,
    lineColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return

    val chartEntries = remember(entries) { entries.toList() }

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

                // X-axis setup (Distance)
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textSize = 10f
                    setDrawGridLines(false)
                    labelCount = 6
                    isGranularityEnabled = true
                }

                // Left Y-axis setup (Heart Rate)
                axisLeft.apply {
                    textSize = 10f
                    setDrawGridLines(true)
                    // Set a reasonable range for heart rate values
                    axisMinimum = 40f  // Typical resting heart rate lower bound
                    axisMaximum = 200f // Upper limit for most exercise heart rates
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

                // Auto-zoom to show all data
                zoom(1f, 1f, 0f, 0f)
            }
        },
        update = { chart ->
            val dataSet = LineDataSet(chartEntries, "Heart Rate").apply {
                color = lineColor.toArgb()
                setDrawFilled(true)
                setFillColor(fillColor.toArgb())
                fillAlpha = 30 // Semi-transparent fill
                lineWidth = 2f
                setDrawCircles(true) // Draw points on the line for heart rate
                circleRadius = 3f
                circleColors = listOf(lineColor.toArgb())
                mode = LineDataSet.Mode.CUBIC_BEZIER // Smooth curve
                setDrawValues(false) // Don't show values above points
            }

            val lineData = LineData(dataSet)
            chart.data = lineData

            // Calculate dynamic granularity based on data range
            val minX = chartEntries.minOfOrNull { it.x } ?: 0f
            val maxX = chartEntries.maxOfOrNull { it.x } ?: 0f
            val range = maxX - minX
            val useMeters = range < 1f // Less than 1 km — show in meters
            chart.xAxis.granularity = if (useMeters) {
                // Show in meters: granularity based on meter range
                val meterRange = range * 1000f
                when {
                    meterRange < 100f -> 10f / 1000f   // every 10m
                    meterRange < 500f -> 50f / 1000f   // every 50m
                    else -> 100f / 1000f                // every 100m
                }
            } else {
                when {
                    range < 5f -> 0.5f
                    range < 20f -> 1f
                    else -> 5f
                }
            }

            // X-axis formatter for distance
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (useMeters) {
                        "${(value * 1000).toInt()} m"
                    } else if (value % 1.0f == 0.0f) {
                        "${value.toInt()} km"
                    } else {
                        String.format("%.1f km", value)
                    }
                }
            }

            // Y-axis formatter for heart rate values
            chart.axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "${value.toInt()} bpm"
                }
            }

            // Force refresh
            chart.invalidate()
        }
    )
}