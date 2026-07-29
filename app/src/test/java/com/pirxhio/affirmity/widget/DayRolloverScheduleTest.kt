package com.pirxhio.affirmity.widget

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class DayRolloverScheduleTest {

    private fun calendarAt(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Calendar =
        Calendar.getInstance().apply {
            set(year, month, day, hour, minute, second)
            set(Calendar.MILLISECOND, 0)
        }

    @Test
    fun `delay from mid-afternoon lands 60 seconds after next midnight`() {
        val now = calendarAt(2026, Calendar.JANUARY, 14, 15, 0)

        val delay = DayRolloverSchedule.delayUntilNextMidnightMillis(now.timeInMillis, now)

        val expectedTarget = calendarAt(2026, Calendar.JANUARY, 15, 0, 1, 0)
        assertEquals(expectedTarget.timeInMillis - now.timeInMillis, delay)
    }

    @Test
    fun `delay from just before midnight is short but still lands after rollover`() {
        val now = calendarAt(2026, Calendar.JANUARY, 14, 23, 59, 30)

        val delay = DayRolloverSchedule.delayUntilNextMidnightMillis(now.timeInMillis, now)

        val expectedTarget = calendarAt(2026, Calendar.JANUARY, 15, 0, 1, 0)
        assertEquals(expectedTarget.timeInMillis - now.timeInMillis, delay)
    }
}
