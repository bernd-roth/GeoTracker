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

@Composable
fun RealLineChart(
    entries: List<Entry>,
    lineColor: Color,
    fillColor: Color,
    xLabel: String,
    yLabel: String,
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

                // X-axis setup
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textSize = 10f
                    setDrawGridLines(false)
                    granularity = 1.0f
                    labelCount = 6
                    isGranularityEnabled = true
                }

                // Left Y-axis setup
                axisLeft.apply {
                    textSize = 10f
                    setDrawGridLines(true)
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
            val dataSet = LineDataSet(chartEntries, yLabel).apply {
                color = lineColor.toArgb()
                setDrawFilled(true)
                setFillColor(fillColor.toArgb())
                fillAlpha = 30 // Semi-transparent fill
                lineWidth = 2f
                setDrawCircles(false) // Don't draw points on the line
                mode = LineDataSet.Mode.CUBIC_BEZIER // Smooth curve
                setDrawValues(false) // Don't show values above points
            }

            val lineData = LineData(dataSet)
            chart.data = lineData

            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value % 1.0f == 0.0f) {
                        "${value.toInt()} km"
                    } else {
                        String.format("%.1f km", value)
                    }
                }
            }

            chart.axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (yLabel.contains("Speed")) {
                        "${value.toInt()} km/h"
                    } else {
                        "${value.toInt()} m"
                    }
                }
            }

            // Force refresh
            chart.invalidate()
        }
    )
}