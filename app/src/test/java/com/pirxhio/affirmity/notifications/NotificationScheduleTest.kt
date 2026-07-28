package com.pirxhio.affirmity.notifications

import java.util.Calendar
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationScheduleTest {

    private fun calendarAt(year: Int, month: Int, day: Int, hour: Int, minute: Int): Calendar =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }

    private fun minuteOfDay(millis: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        return calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
    }

    @Test
    fun `pick falls within configured window on the same day`() {
        val now = calendarAt(2026, Calendar.JANUARY, 10, 6, 0).timeInMillis
        val startMinute = 9 * 60
        val endMinute = 21 * 60
        // seeded random always picks the minimum offset -> start of window
        val random = Random(0)

        val result = NotificationSchedule.nextTriggerAtMillis(now, startMinute, endMinute, random)

        assertTrue(result > now)
        val minute = minuteOfDay(result)
        assertTrue("minute=$minute", minute in startMinute..endMinute)
    }

    @Test
    fun `rolls to next day when today's window has already passed`() {
        val now = calendarAt(2026, Calendar.JANUARY, 10, 23, 0).timeInMillis
        val startMinute = 9 * 60
        val endMinute = 21 * 60
        val random = Random(1)

        val result = NotificationSchedule.nextTriggerAtMillis(now, startMinute, endMinute, random)

        assertTrue(result > now)
        val nowDay = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.DAY_OF_YEAR)
        val resultDay = Calendar.getInstance().apply { timeInMillis = result }.get(Calendar.DAY_OF_YEAR)
        assertEquals(nowDay + 1, resultDay)
    }

    @Test
    fun `late fire after window end still rolls to next day's window`() {
        // Doze-deferred worker running well after the window closed.
        val now = calendarAt(2026, Calendar.JANUARY, 10, 23, 40).timeInMillis
        val startMinute = 9 * 60
        val endMinute = 21 * 60

        val result = NotificationSchedule.nextTriggerAtMillis(now, startMinute, endMinute, Random(2))

        val minute = minuteOfDay(result)
        assertTrue(result > now)
        assertTrue("minute=$minute", minute in startMinute..endMinute)
    }

    @Test
    fun `degenerate window with equal start and end always picks that exact minute`() {
        val now = calendarAt(2026, Calendar.JANUARY, 10, 6, 0).timeInMillis
        val fixedMinute = 12 * 60

        val result = NotificationSchedule.nextTriggerAtMillis(now, fixedMinute, fixedMinute, Random(3))

        assertEquals(fixedMinute, minuteOfDay(result))
    }

    @Test
    fun `inverted window is clamped to a zero-length span at start`() {
        val now = calendarAt(2026, Calendar.JANUARY, 10, 6, 0).timeInMillis
        val startMinute = 21 * 60
        val endMinute = 9 * 60 // inverted

        val result = NotificationSchedule.nextTriggerAtMillis(now, startMinute, endMinute, Random(4))

        assertEquals(startMinute, minuteOfDay(result))
    }
}
