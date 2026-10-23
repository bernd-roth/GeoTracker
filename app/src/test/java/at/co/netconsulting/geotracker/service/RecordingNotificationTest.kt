package at.co.netconsulting.geotracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import androidx.core.app.NotificationCompat
import at.co.netconsulting.geotracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class RecordingNotificationTest {
    private lateinit var service: ForegroundService
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        // Attach the service without onCreate's GPS, sensor and recovery startup.
        service = Robolectric.buildService(ForegroundService::class.java).get()
        manager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel("recording-test", "Recording", NotificationManager.IMPORTANCE_LOW)
        )
        setField("notificationManager", manager)
        setField("notificationBuilder", NotificationCompat.Builder(service, "recording-test")
            .setSmallIcon(R.drawable.ic_launcher_background))
    }

    @Test
    fun queuedUpdateCannotRecreateNotificationAfterRemoval() {
        updateNotification()
        assertEquals(1, manager.activeNotifications.size)

        // Reproduce a worker update waiting behind stop/finalization on the main thread.
        thread { updateNotification() }.join()
        removeNotification()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(manager.activeNotifications.isEmpty())
        updateNotification()
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun pausedRecordingDisablesSystemTimerAndResumeEnablesIt() {
        updateNotification()
        assertTrue(notification().extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))

        setField("isPaused", true)
        updateNotification()
        assertFalse(notification().extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertFalse(notification().extras.getBoolean(Notification.EXTRA_SHOW_WHEN))

        setField("isPaused", false)
        updateNotification()
        assertTrue(notification().extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(notification().extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        removeNotification()
    }

    private fun notification(): Notification = manager.activeNotifications.single().notification

    private fun updateNotification() {
        ForegroundService::class.java.getDeclaredMethod(
            "updateNotification", String::class.java, String::class.java, Long::class.javaPrimitiveType
        ).apply { isAccessible = true }.invoke(service, "11.94 km", "Total Recording: 01:25:51", 5_151_000L)
    }

    private fun removeNotification() {
        ForegroundService::class.java.getDeclaredMethod("removeRecordingNotification")
            .apply { isAccessible = true }.invoke(service)
    }

    private fun setField(name: String, value: Any) {
        ForegroundService::class.java.getDeclaredField(name)
            .apply { isAccessible = true }.set(service, value)
    }
}
