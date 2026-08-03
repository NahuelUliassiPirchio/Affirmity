package com.pirxhio.affirmity.data

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class DayClockTest {

    private fun calendarAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
        zone: TimeZone = TimeZone.getDefault(),
    ): Calendar = Calendar.getInstance(zone).apply {
        set(year, month, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }

    @Test
    fun `epochDay is stable across the same calendar day`() {
        val morning = calendarAt(2026, Calendar.JANUARY, 14, hour = 0, minute = 1)
        val night = calendarAt(2026, Calendar.JANUARY, 14, hour = 23, minute = 59)

        assertEquals(DayClock.epochDay(morning), DayClock.epochDay(night))
    }

    @Test
    fun `epochDay advances by one for the next calendar day`() {
        val day1 = calendarAt(2026, Calendar.JANUARY, 14)
        val day2 = calendarAt(2026, Calendar.JANUARY, 15)

        assertEquals(DayClock.epochDay(day1) + 1, DayClock.epochDay(day2))
    }

    @Test
    fun `rollingWindowStartEpochDay is 6 days before the given calendar day`() {
        val today = calendarAt(2026, Calendar.JANUARY, 14)
        val sixDaysAgo = calendarAt(2026, Calendar.JANUARY, 8)

        assertEquals(DayClock.epochDay(sixDaysAgo), DayClock.rollingWindowStartEpochDay(today))
    }

    @Test
    fun `epochDay and rollingWindowStartEpochDay stay consistent across a DST spring-forward boundary`() {
        val nyZone = TimeZone.getTimeZone("America/New_York")
        // 2026-03-08 is a DST spring-forward Sunday in America/New_York.
        val beforeDst = calendarAt(2026, Calendar.MARCH, 7, hour = 23, minute = 0, zone = nyZone)
        val afterDst = calendarAt(2026, Calendar.MARCH, 8, hour = 3, minute = 0, zone = nyZone)

        assertEquals(DayClock.epochDay(beforeDst) + 1, DayClock.epochDay(afterDst))
        assertEquals(
            DayClock.rollingWindowStartEpochDay(beforeDst) + 1,
            DayClock.rollingWindowStartEpochDay(afterDst),
        )
    }

    @Test
    fun `rollingWindowDayLetters ends on the given calendar day's weekday, oldest first`() {
        val wednesday = calendarAt(2026, Calendar.JANUARY, 14) // Wed

        val letters = DayClock.rollingWindowDayLetters(wednesday)

        assertEquals(7, letters.size)
        assertEquals("M", letters.last()) // today = Wednesday
        assertEquals("J", letters.first()) // 6 days before Wednesday = last Thursday
    }
}
