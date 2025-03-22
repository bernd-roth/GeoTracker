package at.co.netconsulting.geotracker.tools

import android.content.Context
import android.provider.Settings
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class Tools {
    fun provideDateTimeFormat() : String {
        val zonedDateTime = LocalDateTime.now().atZone(ZoneId.systemDefault())
        val epochMilli = zonedDateTime.toInstant().toEpochMilli()
        val startDateTimeFormatted = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(epochMilli),
            ZoneId.systemDefault()
        ).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        return startDateTimeFormatted
    }
    fun generateSessionId(firstname: String, context: Context): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"))
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val random = (0..999999).random().toString().padStart(6, '0')

        return "${firstname}_${timestamp}_${deviceId}_$random"
    }
}