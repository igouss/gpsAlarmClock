package com.elendal.gpsalarmclock

import android.app.NotificationManager
import android.content.Intent
import android.media.MediaPlayer
import io.mockk.*
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmServiceTest {

    @Before
    fun setUp() {
        // Stub MediaPlayer constructor so native calls don't blow up in JVM.
        // MediaPlayer uses native code internally; constructing it in Robolectric/JVM
        // will silently fail or throw depending on the shadow. We stub the whole thing.
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().setAudioAttributes(any()) } just Runs
        every { anyConstructed<MediaPlayer>().setDataSource(any(), any()) } just Runs
        every { anyConstructed<MediaPlayer>().isLooping = any() } just Runs
        every { anyConstructed<MediaPlayer>().prepare() } just Runs
        every { anyConstructed<MediaPlayer>().start() } just Runs
        every { anyConstructed<MediaPlayer>().stop() } just Runs
        every { anyConstructed<MediaPlayer>().release() } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── ACTION_DISMISS ────────────────────────────────────────────────────────

    @Test
    fun `ACTION_DISMISS intent stops service and cancels notification`() {
        val dismissIntent = Intent(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            AlarmService::class.java
        ).apply {
            action = AlarmService.ACTION_DISMISS
            putExtra(AlarmService.EXTRA_ALARM_ID, 1L)
        }

        val controller = Robolectric.buildService(AlarmService::class.java, dismissIntent)
        val service = controller.create().get()

        // First start with a normal intent to put service into foreground state
        val startIntent = Intent(service, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, 1L)
            putExtra(AlarmService.EXTRA_ALARM_LABEL, "Morning")
        }
        service.onStartCommand(startIntent, 0, 1)

        // Now send dismiss
        service.onStartCommand(dismissIntent, 0, 2)

        // Service should have called stopSelf
        Assert.assertTrue(
            "Service should stop itself on ACTION_DISMISS",
            Shadows.shadowOf(service).isStoppedBySelf
        )

        // Notification should be cancelled
        val nm = service.getSystemService(NotificationManager::class.java)
        val shadowNm = Shadows.shadowOf(nm)
        Assert.assertNull(
            "Alarm notification should be cancelled after dismiss",
            shadowNm.getNotification(AlarmService.NOTIFICATION_ID)
        )
    }

    // ── NORMAL START ──────────────────────────────────────────────────────────

    @Test
    fun `normal start calls startForeground and shows notification`() {
        val startIntent = Intent(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            AlarmService::class.java
        ).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, 42L)
            putExtra(AlarmService.EXTRA_ALARM_LABEL, "Wake up")
        }

        val controller = Robolectric.buildService(AlarmService::class.java, startIntent)
        val service = controller.create().startCommand(0, 1).get()

        // Robolectric tracks the last foreground notification
        val shadow = Shadows.shadowOf(service)
        Assert.assertNotNull(
            "startForeground should have been called",
            shadow.lastForegroundNotification
        )
        Assert.assertEquals(AlarmService.NOTIFICATION_ID, shadow.lastForegroundNotificationId)
    }

    // ── ON_DESTROY ────────────────────────────────────────────────────────────

    @Test
    fun `onDestroy releases MediaPlayer`() {
        val startIntent = Intent(
            androidx.test.core.app.ApplicationProvider.getApplicationContext(),
            AlarmService::class.java
        ).apply {
            putExtra(AlarmService.EXTRA_ALARM_ID, 1L)
            putExtra(AlarmService.EXTRA_ALARM_LABEL, "Test")
        }

        val controller = Robolectric.buildService(AlarmService::class.java, startIntent)
        controller.create().startCommand(0, 1).destroy()

        // Verify MediaPlayer.release() was called (via mockkConstructor)
        verify(atLeast = 1) { anyConstructed<MediaPlayer>().release() }
    }

    // ── EDGE CASE: null intent ────────────────────────────────────────────────

    @Test
    fun `null intent does not crash service`() {
        val controller = Robolectric.buildService(AlarmService::class.java, null)
        val service = controller.create().get()

        // Should not throw
        service.onStartCommand(null, 0, 1)
    }

    // ── IGNORED — native MediaPlayer audio ───────────────────────────────────

    @Ignore("MediaPlayer.setDataSource with Settings URI hits native code; " +
            "audio playback not testable in Robolectric JVM environment")
    @Test
    fun `alarm sound plays on normal start`() {
        // This would require a real device or an emulator with audio support.
        // The constructor mock in setUp() already stubs the happy path,
        // so the code path is exercised but actual audio cannot be verified here.
    }
}
