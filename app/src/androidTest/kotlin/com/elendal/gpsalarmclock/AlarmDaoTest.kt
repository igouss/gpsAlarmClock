package com.elendal.gpsalarmclock

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmDaoTest {

    private lateinit var db: AlarmDatabase
    private lateinit var dao: AlarmDao

    @Before
    fun setup() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AlarmDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.alarmDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun testAlarm(
        hour: Int = 7,
        minute: Int = 30,
        label: String = "Morning",
        type: AlarmType = AlarmType.FULL_WEEK,
        enabled: Boolean = true
    ) = Alarm(
        label = label,
        hour = hour,
        minute = minute,
        alarmType = type,
        isEnabled = enabled
    )

    @Test
    fun insertAndGetById() = runBlocking {
        val id = dao.insert(testAlarm())
        val retrieved = dao.getById(id)
        assertNotNull(retrieved)
        assertEquals(7, retrieved!!.hour)
        assertEquals(30, retrieved.minute)
        assertEquals("Morning", retrieved.label)
    }

    @Test
    fun insertMultipleAndGetAll() = runBlocking {
        dao.insert(testAlarm(7, 0, "First"))
        dao.insert(testAlarm(8, 0, "Second"))
        dao.insert(testAlarm(9, 0, "Third"))

        val all = dao.getAllAlarms().first()
        assertEquals(3, all.size)
    }

    @Test
    fun getAllReturnsEmptyInitially() = runBlocking {
        val all = dao.getAllAlarms().first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun updateAlarm() = runBlocking {
        val id = dao.insert(testAlarm(7, 0))
        val inserted = dao.getById(id)!!
        val updated = inserted.copy(hour = 8, minute = 15, label = "Updated")
        dao.update(updated)

        val retrieved = dao.getById(id)
        assertNotNull(retrieved)
        assertEquals(8, retrieved!!.hour)
        assertEquals(15, retrieved.minute)
        assertEquals("Updated", retrieved.label)
    }

    @Test
    fun deleteAlarm() = runBlocking {
        val id = dao.insert(testAlarm())
        val inserted = dao.getById(id)!!
        dao.delete(inserted)

        val retrieved = dao.getById(id)
        assertNull(retrieved)
    }

    @Test
    fun toggleIsEnabled() = runBlocking {
        val id = dao.insert(testAlarm(enabled = true))
        val inserted = dao.getById(id)!!
        assertTrue(inserted.isEnabled)

        dao.update(inserted.copy(isEnabled = false))
        val disabled = dao.getById(id)!!
        assertFalse(disabled.isEnabled)

        dao.update(disabled.copy(isEnabled = true))
        val enabled = dao.getById(id)!!
        assertTrue(enabled.isEnabled)
    }

    @Test
    fun geofencedAlarmPersiststLatLngRadius() = runBlocking {
        val geoAlarm = testAlarm().copy(
            isGeoFenced = true,
            geoFenceLat = 52.5200,
            geoFenceLng = 13.4050,
            geoFenceRadius = 750f
        )
        val id = dao.insert(geoAlarm)
        val retrieved = dao.getById(id)!!

        assertTrue(retrieved.isGeoFenced)
        assertEquals(52.5200, retrieved.geoFenceLat, 0.0001)
        assertEquals(13.4050, retrieved.geoFenceLng, 0.0001)
        assertEquals(750f, retrieved.geoFenceRadius, 0.01f)
    }

    @Test
    fun alarmTypePersistedCorrectly() = runBlocking {
        val types = listOf(
            AlarmType.FULL_WEEK,
            AlarmType.WORKDAY,
            AlarmType.WEEKEND,
            AlarmType.DATE_RANGE
        )
        for (type in types) {
            val id = dao.insert(testAlarm(type = type))
            val retrieved = dao.getById(id)
            assertEquals("AlarmType $type should persist correctly", type, retrieved!!.alarmType)
        }
    }

    @Test
    fun dateRangeFieldsPersisted() = runBlocking {
        val start = 1_700_000_000_000L
        val end = 1_700_500_000_000L
        val alarm = testAlarm(type = AlarmType.DATE_RANGE).copy(
            startDate = start,
            endDate = end
        )
        val id = dao.insert(alarm)
        val retrieved = dao.getById(id)!!

        assertEquals(start, retrieved.startDate)
        assertEquals(end, retrieved.endDate)
    }

    @Test
    fun getByIdReturnsNullForMissingId() = runBlocking {
        val result = dao.getById(999L)
        assertNull(result)
    }
}
