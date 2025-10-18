package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SlopeColorLegend(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color.White.copy(alpha = 0.9f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = "Slope Legend",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Steep decline
        LegendItem(
            color = Color.Red,
            label = "Steep Decline",
            description = "> 8% down",
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Moderate decline
        LegendItem(
            color = Color(0xFFFF6600),
            label = "Moderate Decline",
            description = "3-8% down",
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Gentle decline
        LegendItem(
            color = Color.Yellow,
            label = "Gentle Decline",
            description = "1-3% down",
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Flat
        LegendItem(
            color = Color(0,139,0),
            label = "Flat",
            description = "±1%",
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Gentle incline
        LegendItem(
            color = Color(0xFF87CEEB),
            label = "Gentle Incline",
            description = "1-3% up",
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Moderate incline
        LegendItem(
            color = Color.Blue,
            label = "Moderate Incline",
            description = "3-8% up",
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Steep incline
        LegendItem(
            color = Color(0xFF000080),
            label = "Steep Incline",
            description = "> 8% up"
        )
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp, 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = description,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

/**
 * Compact slope legend for the main map screen
 * Smaller version with just color indicators and slope percentages
 */
@Composable
fun CompactSlopeColorLegend(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                Color.White.copy(alpha = 0.85f),
                RoundedCornerShape(6.dp)
            )
            .padding(8.dp)
    ) {
        Text(
            text = "Slope",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // Steep decline
        CompactLegendItem(
            color = Color.Red,
            description = ">8%↓"
        )

        // Moderate decline
        CompactLegendItem(
            color = Color(0xFFFF6600),
            description = "3-8%↓"
        )

        // Gentle decline
        CompactLegendItem(
            color = Color.Yellow,
            description = "1-3%↓"
        )

        // Flat
        CompactLegendItem(
            color = Color(0,139,0),
            description = "±1%"
        )

        // Gentle incline
        CompactLegendItem(
            color = Color(0xFF87CEEB),
            description = "1-3%↑"
        )

        // Moderate incline
        CompactLegendItem(
            color = Color.Blue,
            description = "3-8%↑"
        )

        // Steep incline
        CompactLegendItem(
            color = Color(0xFF000080),
            description = ">8%↑"
        )
    }
}

@Composable
private fun CompactLegendItem(
    color: Color,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp, 3.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(color)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = description,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}