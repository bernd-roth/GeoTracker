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
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

@Composable
fun BarometerChart(
    pressureEntries: List<Entry>,
    altitudeEntries: List<Entry>,
    modifier: Modifier = Modifier
) {
    if (pressureEntries.isEmpty() && altitudeEntries.isEmpty()) return

    val chartPressureEntries = remember(pressureEntries) { pressureEntries.toList() }
    val chartAltitudeEntries = remember(altitudeEntries) { altitudeEntries.toList() }

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

                // Left Y-axis setup (for pressure in hPa)
                axisLeft.apply {
                    textSize = 10f
                    setDrawGridLines(true)
                    granularity = 1.0f
                    isGranularityEnabled = true
                    textColor = Color(0xFF2196F3).toArgb()
                }

                // Right Y-axis setup (for barometric altitude in m)
                axisRight.apply {
                    isEnabled = true
                    textSize = 10f
                    setDrawGridLines(false)
                    granularity = 1.0f
                    isGranularityEnabled = true
                    textColor = Color(0xFF4CAF50).toArgb()
                }

                // Legend setup
                legend.apply {
                    textSize = 12f
                    verticalAlignment = com.github.mikephil.charting.components.Legend.LegendVerticalAlignment.TOP
                    horizontalAlignment = com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.RIGHT
                    orientation = com.github.mikephil.charting.components.Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                }
            }
        },
        update = { chart ->
            val dataSets = mutableListOf<LineDataSet>()

            // Add pressure data set (left axis)
            if (chartPressureEntries.isNotEmpty()) {
                val pressureDataSet = LineDataSet(chartPressureEntries, "Pressure (hPa)").apply {
                    color = Color(0xFF2196F3).toArgb()
                    setDrawFilled(true)
                    setFillColor(Color(0xFF2196F3).toArgb())
                    fillAlpha = 30
                    lineWidth = 2f
                    setDrawCircles(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    setDrawValues(false)
                    axisDependency = YAxis.AxisDependency.LEFT
                }
                dataSets.add(pressureDataSet)
            }

            // Add barometric altitude data set (right axis)
            if (chartAltitudeEntries.isNotEmpty()) {
                val altitudeDataSet = LineDataSet(chartAltitudeEntries, "Barometric Alt (m)").apply {
                    color = Color(0xFF4CAF50).toArgb()
                    setDrawFilled(true)
                    setFillColor(Color(0xFF4CAF50).toArgb())
                    fillAlpha = 30
                    lineWidth = 2f
                    setDrawCircles(false)
                    mode = LineDataSet.Mode.CUBIC_BEZIER
                    setDrawValues(false)
                    axisDependency = YAxis.AxisDependency.RIGHT
                }
                dataSets.add(altitudeDataSet)
            }

            val lineData = LineData(dataSets as List<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>)
            chart.data = lineData

            // X-axis formatter
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value == value.toInt().toFloat()) {
                        "${value.toInt()} km"
                    } else {
                        String.format("%.1f km", value)
                    }
                }
            }

            // Left Y-axis formatter (pressure)
            chart.axisLeft.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value == value.toInt().toFloat()) {
                        "${value.toInt()} hPa"
                    } else {
                        String.format("%.1f hPa", value)
                    }
                }
            }

            // Right Y-axis formatter (altitude)
            chart.axisRight.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return if (value == value.toInt().toFloat()) {
                        "${value.toInt()} m"
                    } else {
                        String.format("%.1f m", value)
                    }
                }
            }

            // Force refresh
            chart.invalidate()
        }
    )
}