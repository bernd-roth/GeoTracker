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
 * A composable that displays heart rate data in relation to speed.
 * X-axis: Speed (km/h), Y-axis: Heart Rate (bpm)
 */
@Composable
fun HeartRateSpeedChart(
    entries: List<Entry>,
    lineColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return

    val chartEntries = remember(entries) {
        // Sort by speed (X value) for a coherent chart
        entries.sortedBy { it.x }
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

                // X-axis setup (Speed)
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textSize = 10f
                    setDrawGridLines(false)
                    labelCount = 6
                    isGranularityEnabled = true
                    granularity = 1f
                }

                // Left Y-axis setup (Heart Rate)
                axisLeft.apply {
                    textSize = 10f
                    setDrawGridLines(true)
                    axisMinimum = 40f
                    axisMaximum = 200f
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

                zoom(1f, 1f, 0f, 0f)
            }
        },
        update = { chart ->
            val dataSet = LineDataSet(chartEntries, "Heart Rate").apply {
                color = lineColor.toArgb()
                setDrawFilled(true)
                setFillColor(fillColor.toArgb())
                fillAlpha = 30
                lineWidth = 2f
                setDrawCircles(true)
                circleRadius = 3f
                circleColors = listOf(lineColor.toArgb())
                mode = LineDataSet.Mode.CUBIC_BEZIER
                setDrawValues(false)
            }

            val lineData = LineData(dataSet)
            chart.data = lineData

            // X-axis formatter for speed
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.0f km/h", value)
                }
            }

            // Y-axis formatter for heart rate
            chart.axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "${value.toInt()} bpm"
                }
            }

            chart.invalidate()
        }
    )
}
