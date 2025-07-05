package at.co.netconsulting.geotracker.tools

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

class AlarmPermissionHelper(private val activity: ComponentActivity) {

    private var permissionLauncher: ActivityResultLauncher<Intent>? = null
    private var onPermissionResult: ((Boolean) -> Unit)? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher = activity.registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val hasPermission = checkExactAlarmPermission(activity)
                Log.d("AlarmPermissionHelper", "Exact alarm permission result: $hasPermission")
                onPermissionResult?.invoke(hasPermission)
            }
        }
    }

    companion object {
        fun checkExactAlarmPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.canScheduleExactAlarms()
            } else {
                true // Permission not required on older versions
            }
        }

        fun requestExactAlarmPermission(
            activity: ComponentActivity,
            onResult: (Boolean) -> Unit = {}
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                if (!alarmManager.canScheduleExactAlarms()) {
                    Log.d("AlarmPermissionHelper", "Requesting exact alarm permission")

                    // Create intent to open exact alarm settings
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }

                    try {
                        activity.startActivity(intent)
                        onResult(false) // Will need to check again after user returns
                    } catch (e: Exception) {
                        Log.e("AlarmPermissionHelper", "Failed to open exact alarm settings", e)
                        onResult(false)
                    }
                } else {
                    onResult(true)
                }
            } else {
                onResult(true) // Permission not required on older versions
            }
        }
    }

    fun requestPermission(onResult: (Boolean) -> Unit) {
        this.onPermissionResult = onResult

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = activity.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                permissionLauncher?.launch(intent)
            } else {
                onResult(true)
            }
        } else {
            onResult(true)
        }
    }
}