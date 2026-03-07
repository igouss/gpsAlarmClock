package com.elendal.gpsalarmclock

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmSchedulerTest {

    private lateinit var context: Context
    private lateinit var scheduler: AlarmScheduler

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        scheduler = AlarmScheduler(context)
    }

    private fun calendarAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar {
        return Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun alarm(
        hour: Int,
        minute: Int,
        type: AlarmType = AlarmType.FULL_WEEK,
        enabled: Boolean = true,
        startDate: Long? = null,
        endDate: Long? = null
    ) = Alarm(
        id = 1L,
        label = "test",
        hour = hour,
        minute = minute,
        alarmType = type,
        isEnabled = enabled,
        startDate = startDate,
        endDate = endDate
    )

    // ── FULL_WEEK ────────────────────────────────────────────────────────────

    @Test
    fun `FULL_WEEK alarm schedules for today if time not yet passed`() {
        // Wednesday 2026-03-04 at 08:00 → alarm at 09:00 same day
        val now = calendarAt(2026, Calendar.MARCH, 4, 8, 0)
        val result = scheduler.computeNextTriggerTime(alarm(9, 0), now)

        assertNotNull(result)
        val expected = calendarAt(2026, Calendar.MARCH, 4, 9, 0)
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `FULL_WEEK alarm schedules for tomorrow if time already passed`() {
        // Wednesday 2026-03-04 at 10:00 → alarm at 09:00, already passed → tomorrow
        val now = calendarAt(2026, Calendar.MARCH, 4, 10, 0)
        val result = scheduler.computeNextTriggerTime(alarm(9, 0), now)

        assertNotNull(result)
        val expected = calendarAt(2026, Calendar.MARCH, 5, 9, 0)
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `FULL_WEEK alarm schedules for today when time equals now exactly`() {
        // Exact same minute → treated as "already passed" (candidate <= now), so tomorrow
        val now = calendarAt(2026, Calendar.MARCH, 4, 9, 0)
        val result = scheduler.computeNextTriggerTime(alarm(9, 0), now)

        assertNotNull(result)
        val expected = calendarAt(2026, Calendar.MARCH, 5, 9, 0)
        assertEquals(expected.timeInMillis, result)
    }

    // ── WORKDAY ──────────────────────────────────────────────────────────────

    @Test
    fun `WORKDAY alarm on Friday evening schedules for Monday`() {
        // Friday 2026-03-06 at 18:00 → alarm at 07:00 already passed → skip Sat/Sun → Monday 2026-03-09
        val now = calendarAt(2026, Calendar.MARCH, 6, 18, 0)
        val result = scheduler.computeNextTriggerTime(alarm(7, 0, AlarmType.WORKDAY), now)

        assertNotNull(result)
        val expected = calendarAt(2026, Calendar.MARCH, 9, 7, 0)
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `WORKDAY alarm on Monday morning schedules for today`() {
        // Monday 2026-03-09 at 06:00 → alarm at 08:00 not yet passed
        val now = calendarAt(2026, Calendar.MARCH, 9, 6, 0)
        val result = scheduler.computeNextTriggerTime(alarm(8, 0, AlarmType.WORKDAY), now)

        assertNotNull(result)
        val expected = calendarAt(2026, Calendar.MARCH, 9, 8, 0)
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `WORKDAY alarm on Saturday schedules for Monday`() {
        // Saturday 2026-03-07 at 10:00 → skip Sat/Sun → Monday 2026-03-09
        val now = calendarAt(2026, Calendar.MARCH, 7, 10, 0)
        val result = scheduler.computeNextTriggerTime(alarm(8, 0, AlarmType.WORKDAY), now)

        assertNotNull(result)
        val expected = calendarAt(2026, Calendar.MARCH, 9, 8, 0)
        assertEquals(expected.timeInMillis, result)
    }

    // ── WEEKEND ──────────────────────────────────────────────────────────────

    @Test
    fun `WEEKEND alarm on Monday schedules for Saturday`() {
        // Monday 2026-03-09 at 10:00 → skip Mon-Fri → Saturday 2026-03-14
        val now = calendarAt(2026, Calendar.MARCH, 9, 10, 0)
        val result = scheduler.computeNextTriggerTime(alarm(9, 0, AlarmType.WEEKEND), now)

        assertNotNull(result)
        val expected = calendarAt(2026, Calendar.MARCH, 14, 9, 0)
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `WEEKEND alarm on Sunday morning schedules for today`() {
        // Sunday 2026-03-08 at 07:00 → alarm at 09:00 not yet passed
        val now = calendarAt(2026, Calendar.MARCH, 8, 7, 0)
        val result = scheduler.computeNextTriggerTime(alarm(9, 0, AlarmType.WEEKEND), now)

        assertNotNull(result)
        val expected = calendarAt(2026, Calendar.MARCH, 8, 9, 0)
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `WEEKEND alarm on Sunday evening schedules for next Saturday`() {
        // Sunday 2026-03-08 at 22:00 → alarm at 09:00 already passed → skip Mon-Fri → Saturday 2026-03-14
        val now = calendarAt(2026, Calendar.MARCH, 8, 22, 0)
        val result = scheduler.computeNextTriggerTime(alarm(9, 0, AlarmType.WEEKEND), now)

        assertNotNull(result)
        val expected = calendarAt(2026, Calendar.MARCH, 14, 9, 0)
        assertEquals(expected.timeInMillis, result)
    }

    // ── DATE_RANGE ───────────────────────────────────────────────────────────

    @Test
    fun `DATE_RANGE alarm fires within range`() {
        // Range: 2026-03-05 to 2026-03-10
        // Now: 2026-03-06 at 08:00 → alarm at 09:00 same day
        val rangeStart = calendarAt(2026, Calendar.MARCH, 5, 0, 0).timeInMillis
        val rangeEnd = calendarAt(2026, Calendar.MARCH, 10, 0, 0).timeInMillis
        val now = calendarAt(2026, Calendar.MARCH, 6, 8, 0)

        val result = scheduler.computeNextTriggerTime(
            alarm(9, 0, AlarmType.DATE_RANGE, startDate = rangeStart, endDate = rangeEnd),
            now
        )

        assertNotNull(result)
        val expected = calendarAt(2026, Calendar.MARCH, 6, 9, 0)
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `DATE_RANGE alarm returns null when current date is before range start`() {
        // Range: 2026-03-10 to 2026-03-15, but we're on 2026-03-06
        val rangeStart = calendarAt(2026, Calendar.MARCH, 10, 0, 0).timeInMillis
        val rangeEnd = calendarAt(2026, Calendar.MARCH, 15, 0, 0).timeInMillis
        val now = calendarAt(2026, Calendar.MARCH, 6, 8, 0)

        val result = scheduler.computeNextTriggerTime(
            alarm(9, 0, AlarmType.DATE_RANGE, startDate = rangeStart, endDate = rangeEnd),
            now
        )

        // 8-day search from 2026-03-07 to 2026-03-14 — only 2026-03-10..14 overlap with range
        // 2026-03-10 is within 8 days, so it should NOT be null
        assertNotNull(result)
        val expected = calendarAt(2026, Calendar.MARCH, 10, 9, 0)
        assertEquals(expected.timeInMillis, result)
    }

    @Test
    fun `DATE_RANGE alarm returns null when current date is after range end`() {
        // Range: 2026-02-01 to 2026-02-28, but we're on 2026-03-07
        val rangeStart = calendarAt(2026, Calendar.FEBRUARY, 1, 0, 0).timeInMillis
        val rangeEnd = calendarAt(2026, Calendar.FEBRUARY, 28, 0, 0).timeInMillis
        val now = calendarAt(2026, Calendar.MARCH, 7, 8, 0)

        val result = scheduler.computeNextTriggerTime(
            alarm(9, 0, AlarmType.DATE_RANGE, startDate = rangeStart, endDate = rangeEnd),
            now
        )

        assertNull(result)
    }

    @Test
    fun `DATE_RANGE alarm returns null when range is entirely beyond 8-day window`() {
        // Range starts 30 days from now
        val rangeStart = calendarAt(2026, Calendar.APRIL, 6, 0, 0).timeInMillis
        val rangeEnd = calendarAt(2026, Calendar.APRIL, 10, 0, 0).timeInMillis
        val now = calendarAt(2026, Calendar.MARCH, 7, 8, 0)

        val result = scheduler.computeNextTriggerTime(
            alarm(9, 0, AlarmType.DATE_RANGE, startDate = rangeStart, endDate = rangeEnd),
            now
        )

        assertNull(result)
    }

    // ── DISABLED ─────────────────────────────────────────────────────────────

    @Test
    fun `disabled alarm returns null`() {
        val now = calendarAt(2026, Calendar.MARCH, 4, 8, 0)
        val result = scheduler.computeNextTriggerTime(alarm(9, 0, enabled = false), now)
        assertNull(result)
    }
}
