package at.co.netconsulting.geotracker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

    var selectedOption by remember {
        mutableStateOf(sharedPreferences.getString("selectedOption", "option1") ?: "option1")
    }
    var option1Text by remember {
        mutableStateOf(sharedPreferences.getString("option1Text", "") ?: "")
    }
    var option2Text by remember {
        mutableStateOf(sharedPreferences.getString("option2Text", "") ?: "")
    }

    val isBatteryOptimized = isBatteryOptimizationIgnored(context)
    var isOptimized by remember { mutableStateOf(isBatteryOptimized) }

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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = weight.toString(),
            onValueChange = { input ->
                weight = input.toFloatOrNull() ?: weight
            },
            label = { Text("Weight (kg)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = websocketserver,
            onValueChange = { websocketserver = it },
            label = { Text("Websocket ip address") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
                    websocketserver,
                    selectedOption,
                    option1Text,
                    option2Text
                )

                Toast.makeText(context, "All settings saved", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    context.startActivity(intent)
}

fun saveAllSettings(
    sharedPreferences: SharedPreferences,
    firstName: String,
    lastName: String,
    birthDate: String,
    height: Float,
    weight: Float,
    websocketserver: String,
    selectedOption: String,
    option1Text: String,
    option2Text: String
) {
    sharedPreferences.edit().apply {
        putString("firstname", firstName)
        putString("lastname", lastName)
        putString("birthdate", birthDate)
        putFloat("height", height)
        putFloat("weight", weight)
        putString("websocketserver", websocketserver)
        putString("selectedOption", selectedOption)
        putString("option1Text", option1Text)
        putString("option2Text", option2Text)
        apply()
    }
}

fun isBatteryOptimizationIgnored(context: Context): Any {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val packageName = context.packageName
    return powerManager.isIgnoringBatteryOptimizations(packageName)
}
