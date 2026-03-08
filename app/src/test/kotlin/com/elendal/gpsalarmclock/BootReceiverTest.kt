package com.elendal.gpsalarmclock

import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * BootReceiver unit tests using Robolectric.
 *
 * Like AlarmReceiver, BootReceiver calls goAsync() and launches on Dispatchers.IO.
 * We use UnconfinedTestDispatcher for Main and Thread.sleep(300) to wait for the
 * IO thread to complete before asserting.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BootReceiverTest {

    private lateinit var application: Application
    private lateinit var db: AlarmDatabase

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        application = ApplicationProvider.getApplicationContext()

        val inMemoryDb = Room.inMemoryDatabaseBuilder(
            application,
            AlarmDatabase::class.java
        ).allowMainThreadQueries().build()

        val field = AlarmDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, inMemoryDb)
        db = inMemoryDb
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
        val field = AlarmDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun insert(alarm: Alarm) = runBlocking { db.alarmDao().insert(alarm) }

    private fun enabledAlarm(id: Long, hour: Int = 8) = Alarm(
        id = id, label = "Enabled", hour = hour, minute = 0,
        alarmType = AlarmType.FULL_WEEK, isEnabled = true
    )

    private fun disabledAlarm(id: Long) = Alarm(
        id = id, label = "Disabled", hour = 9, minute = 0,
        alarmType = AlarmType.FULL_WEEK, isEnabled = false
    )

    private fun bootIntent() = Intent(Intent.ACTION_BOOT_COMPLETED)

    private fun awaitReceiver() = Thread.sleep(300)

    private fun shadowAlarmManager() =
        Shadows.shadowOf(application.getSystemService(AlarmManager::class.java))

    @Test
    fun `BOOT_COMPLETED reschedules all enabled alarms`() {
        insert(enabledAlarm(1L, 7))
        insert(enabledAlarm(2L, 8))

        BootReceiver().onReceive(application, bootIntent())
        awaitReceiver()

        Assert.assertEquals(
            "Both enabled alarms should be scheduled",
            2,
            shadowAlarmManager().scheduledAlarms.size
        )
    }

    @Test
    fun `BOOT_COMPLETED does not schedule disabled alarms`() {
        insert(enabledAlarm(1L))
        insert(disabledAlarm(2L))

        BootReceiver().onReceive(application, bootIntent())
        awaitReceiver()

        Assert.assertEquals(
            "Only enabled alarm should be scheduled",
            1,
            shadowAlarmManager().scheduledAlarms.size
        )
    }

    @Test
    fun `BOOT_COMPLETED with empty DB schedules nothing`() {
        BootReceiver().onReceive(application, bootIntent())
        awaitReceiver()

        Assert.assertEquals(0, shadowAlarmManager().scheduledAlarms.size)
    }

    @Test
    fun `wrong intent action does nothing`() {
        insert(enabledAlarm(1L))

        BootReceiver().onReceive(application, Intent("com.some.OTHER_ACTION"))
        // No goAsync(), no coroutine — returns immediately

        Assert.assertEquals(
            "Non-boot intent should not schedule any alarms",
            0,
            shadowAlarmManager().scheduledAlarms.size
        )
    }

    @Test
    fun `only disabled alarms in DB schedules nothing`() {
        insert(disabledAlarm(1L))
        insert(disabledAlarm(2L))

        BootReceiver().onReceive(application, bootIntent())
        awaitReceiver()

        Assert.assertEquals(0, shadowAlarmManager().scheduledAlarms.size)
    }
}
