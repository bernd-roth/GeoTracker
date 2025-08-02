package at.co.netconsulting.geotracker.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// Simple direct input for distance
@Composable
fun DistanceDropdown(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Track the text input separately from the Int value
    var textInput by remember { mutableStateOf(value.toString()) }

    Column(modifier = modifier) {
        Text(
            text = "Minimum distance change for updates (in meters)",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = textInput,
            onValueChange = { input ->
                // Always update the text first
                textInput = input

                // Then try to parse the number
                val newValue = input.toIntOrNull()
                if (newValue != null && newValue in 1..30) {
                    onValueChange(newValue)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            supportingText = { Text("Enter a value between 1-30 meters") }
        )
    }
}

// Simple direct input for time
@Composable
fun TimeDropdown(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf(value.toString()) }

    Column(modifier = modifier) {
        Text(
            text = "Minimum time between updates (in seconds)",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = textInput,
            onValueChange = { input ->
                // Always update the text first
                textInput = input

                // Then try to parse the number
                val newValue = input.toIntOrNull()
                if (newValue != null && newValue in 1..20) {
                    onValueChange(newValue)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number
            ),
            supportingText = { Text("Enter a value between 1-20 seconds") }
        )
    }
}

@Composable
fun ManualInputOption(
    distanceValue: Int,
    timeValue: Int,
    onDistanceChange: (Int) -> Unit,
    onTimeChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
}

@Composable
fun VoiceAnnouncementDropdown(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var textInput by remember { mutableStateOf(value.toString()) }

    Column(modifier = modifier) {
        Text(
            text = "Enter a value to get a voice message every ... kilometer (0 means no voice message at all)",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))

        OutlinedTextField(
            value = textInput,
            onValueChange = { input ->
                // Always update the text first
                textInput = input

                // Then try to parse the number
                val newValue = input.toIntOrNull()
                if (newValue != null && newValue in 0..100) {
                    onValueChange(newValue)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.Number
            )
        )
    }
}