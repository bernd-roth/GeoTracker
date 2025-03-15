package at.co.netconsulting.geotracker.composables

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val sharedPreferences = remember {
        context.getSharedPreferences("UserSettings", Context.MODE_PRIVATE)
    }

    val savedState = sharedPreferences.getBoolean("batteryOptimizationState", true)
    var isBatteryOptimizationIgnoredState by remember { mutableStateOf(savedState) }

    var firstName by remember {
        mutableStateOf(sharedPreferences.getString("firstname", "") ?: "")
    }
    var lastName by remember {
        mutableStateOf(sharedPreferences.getString("lastname", "") ?: "")
    }
    var birthDate by remember {
        mutableStateOf(sharedPreferences.getString("birthdate", "") ?: "")
    }
    var height by remember { mutableStateOf(sharedPreferences.getFloat("height", 0f)) }
    var weight by remember { mutableStateOf(sharedPreferences.getFloat("weight", 0f)) }
    var websocketserver by remember {
        mutableStateOf(sharedPreferences.getString("websocketserver", "") ?: "")
    }

    val isBatteryOptimizationIgnored = isBatteryOptimizationIgnored(context)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .background(Color.LightGray)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = firstName,
            onValueChange = { firstName = it },
            label = { Text("Firstname") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = lastName,
            onValueChange = { lastName = it },
            label = { Text("Lastname") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = birthDate,
            onValueChange = { birthDate = it },
            label = { Text("Birthdate (YYYY-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = height.toString(),
            onValueChange = { input ->
                height = input.toFloatOrNull() ?: height
            },
            label = { Text("Height (cm)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = weight.toString(),
            onValueChange = { input ->
                weight = input.toFloatOrNull() ?: weight
            },
            label = { Text("Weight (kg)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = websocketserver,
            onValueChange = { websocketserver = it },
            label = { Text("Websocket ip address") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Battery Optimization",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp)
        )
        Switch(
            checked = isBatteryOptimizationIgnoredState,
            onCheckedChange = { isChecked ->
                isBatteryOptimizationIgnoredState = isChecked
                sharedPreferences.edit()
                    .putBoolean("batteryOptimizationState", isChecked)
                    .apply()

                if (isChecked) {
                    requestIgnoreBatteryOptimizations(context)
                } else {
                    Toast.makeText(
                        context,
                        "Background usage might still be enabled. Please disable manually.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                saveAllSettings(
                    sharedPreferences,
                    firstName,
                    lastName,
                    birthDate,
                    height,
                    weight,
                    websocketserver
                )

                Toast.makeText(context, "All settings saved", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}

private fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val packageName = context.packageName
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

private fun saveAllSettings(
    sharedPreferences: SharedPreferences,
    firstName: String,
    lastName: String,
    birthDate: String,
    height: Float,
    weight: Float,
    websocketserver: String
) {
    sharedPreferences.edit().apply {
        putString("firstname", firstName)
        putString("lastname", lastName)
        putString("birthdate", birthDate)
        putFloat("height", height)
        putFloat("weight", weight)
        putString("websocketserver", websocketserver)
        apply()
    }
}