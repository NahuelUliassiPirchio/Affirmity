package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCalendarStatsTest {

    // Fixed timezone template so leading-blanks/day-count/epochDay math is deterministic
    // regardless of the machine running the test (mirrors DayClockTest's `calendarAt` pattern).
    private fun templateAt(year: Int, month0: Int, day: Int): Calendar =
        Calendar.getInstance(TimeZone.getTimeZone("America/New_York")).apply {
            set(year, month0, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
            firstDayOfWeek = Calendar.SUNDAY
        }

    // --- HistoryMonthLayout.of: leading blanks -------------------------------------------------

    @Test
    fun `leading blanks for a month starting on Sunday is zero when firstDayOfWeek is Sunday`() {
        // 2026-02-01 is a Sunday.
        val layout = HistoryMonthLayout.of(2026, Calendar.FEBRUARY, templateAt(2026, Calendar.FEBRUARY, 1))

        assertEquals(0, layout.leadingBlanks)
    }

    @Test
    fun `leading blanks counts the offset from firstDayOfWeek to the month's first weekday`() {
        // 2026-01-01 is a Thursday. Sun..Sat: Thu is offset 4 from Sunday.
        val layout = HistoryMonthLayout.of(2026, Calendar.JANUARY, templateAt(2026, Calendar.JANUARY, 1))

        assertEquals(4, layout.leadingBlanks)
    }

    @Test
    fun `leading blanks rotates when firstDayOfWeek is Monday`() {
        val template = templateAt(2026, Calendar.JANUARY, 1).apply { firstDayOfWeek = Calendar.MONDAY }
        // 2026-01-01 is a Thursday. Mon..Sun: Thu is offset 3 from Monday.
        val layout = HistoryMonthLayout.of(2026, Calendar.JANUARY, template)

        assertEquals(3, layout.leadingBlanks)
    }

    // --- HistoryMonthLayout.of: day counts for 28/29/30/31-day months --------------------------

    @Test
    fun `day count is 31 for January`() {
        val layout = HistoryMonthLayout.of(2026, Calendar.JANUARY, templateAt(2026, Calendar.JANUARY, 1))
        assertEquals(31, layout.dayCount)
    }

    @Test
    fun `day count is 30 for April`() {
        val layout = HistoryMonthLayout.of(2026, Calendar.APRIL, templateAt(2026, Calendar.APRIL, 1))
        assertEquals(30, layout.dayCount)
    }

    @Test
    fun `day count is 28 for February in a non-leap year`() {
        val layout = HistoryMonthLayout.of(2026, Calendar.FEBRUARY, templateAt(2026, Calendar.FEBRUARY, 1))
        assertEquals(28, layout.dayCount)
    }

    @Test
    fun `day count is 29 for February in a leap year`() {
        val layout = HistoryMonthLayout.of(2028, Calendar.FEBRUARY, templateAt(2028, Calendar.FEBRUARY, 1))
        assertEquals(29, layout.dayCount)
    }

    // --- HistoryMonthLayout.of: epochDay matches DayClock ---------------------------------------

    @Test
    fun `each cell's epochDay matches DayClock epochDay for that calendar date`() {
        val template = templateAt(2026, Calendar.JANUARY, 1)
        val layout = HistoryMonthLayout.of(2026, Calendar.JANUARY, template)

        val expectedFirst = DayClock.epochDay(templateAt(2026, Calendar.JANUARY, 1))
        val expectedLast = DayClock.epochDay(templateAt(2026, Calendar.JANUARY, 31))

        assertEquals(expectedFirst, layout.cells.first().epochDay)
        assertEquals(expectedLast, layout.cells.last().epochDay)
        assertEquals(1, layout.cells.first().dayOfMonth)
        assertEquals(31, layout.cells.last().dayOfMonth)
    }

    @Test
    fun `epochDay increases by exactly one across a DST boundary`() {
        // 2026-03-08 is a DST spring-forward Sunday in America/New_York.
        val template = templateAt(2026, Calendar.MARCH, 1)
        val layout = HistoryMonthLayout.of(2026, Calendar.MARCH, template)

        val day7 = layout.cells[6].epochDay // March 7
        val day8 = layout.cells[7].epochDay // March 8

        assertEquals(day7 + 1, day8)
    }

    // --- HistoryCalendarStats.dayStates: precedence ---------------------------------------------

    private fun layoutFor(year: Int, month0: Int): HistoryMonthLayout =
        HistoryMonthLayout.of(year, month0, templateAt(year, month0, 1))

    @Test
    fun `future day beyond today is FUTURE even if it has a completion row`() {
        val layout = layoutFor(2026, Calendar.JANUARY)
        val futureDay = layout.cells.last().epochDay
        val today = layout.cells.first().epochDay
        val slice = CompletionHistorySlice(
            rows = listOf(DailyCompletionEntity(epochDay = futureDay, affirmationDone = true)),
            healedDays = emptySet(),
        )

        val month = HistoryCalendarStats.dayStates(
            layout = layout,
            slice = slice,
            todayEpochDay = today,
            earliestEpochDay = null,
            isDone = { it.affirmationDone },
        )

        assertEquals(HistoryDayStatus.FUTURE, month.days.last().status)
    }

    @Test
    fun `day before the earliest recorded row is BEFORE_HISTORY`() {
        val layout = layoutFor(2026, Calendar.JANUARY)
        val today = layout.cells.last().epochDay
        val earliest = layout.cells[10].epochDay // day 11

        val month = HistoryCalendarStats.dayStates(
            layout = layout,
            slice = CompletionHistorySlice(rows = emptyList(), healedDays = emptySet()),
            todayEpochDay = today,
            earliestEpochDay = earliest,
            isDone = { it.affirmationDone },
        )

        assertEquals(HistoryDayStatus.BEFORE_HISTORY, month.days[0].status) // day 1
        assertEquals(HistoryDayStatus.NOT_DONE, month.days[10].status) // day 11 == earliest, not before it
    }

    @Test
    fun `null earliest treats the current month as the lower bound -- no BEFORE_HISTORY days`() {
        val layout = layoutFor(2026, Calendar.JANUARY)
        val today = layout.cells.last().epochDay

        val month = HistoryCalendarStats.dayStates(
            layout = layout,
            slice = CompletionHistorySlice(rows = emptyList(), healedDays = emptySet()),
            todayEpochDay = today,
            earliestEpochDay = null,
            isDone = { it.affirmationDone },
        )

        assertTrue(month.days.none { it.status == HistoryDayStatus.BEFORE_HISTORY })
        assertTrue(month.days.dropLast(0).all { it.status == HistoryDayStatus.NOT_DONE })
    }

    @Test
    fun `healed day outranks a done row for the same day`() {
        val layout = layoutFor(2026, Calendar.JANUARY)
        val today = layout.cells.last().epochDay
        val healedDay = layout.cells[4].epochDay // day 5

        val slice = CompletionHistorySlice(
            rows = listOf(DailyCompletionEntity(epochDay = healedDay, affirmationDone = true)),
            healedDays = setOf(healedDay),
        )

        val month = HistoryCalendarStats.dayStates(
            layout = layout,
            slice = slice,
            todayEpochDay = today,
            earliestEpochDay = null,
            isDone = { it.affirmationDone },
        )

        assertEquals(HistoryDayStatus.HEALED, month.days[4].status)
    }

    @Test
    fun `done day with a matching row and no healer use is DONE`() {
        val layout = layoutFor(2026, Calendar.JANUARY)
        val today = layout.cells.last().epochDay
        val doneDay = layout.cells[4].epochDay // day 5

        val slice = CompletionHistorySlice(
            rows = listOf(DailyCompletionEntity(epochDay = doneDay, affirmationDone = true)),
            healedDays = emptySet(),
        )

        val month = HistoryCalendarStats.dayStates(
            layout = layout,
            slice = slice,
            todayEpochDay = today,
            earliestEpochDay = null,
            isDone = { it.affirmationDone },
        )

        assertEquals(HistoryDayStatus.DONE, month.days[4].status)
    }

    @Test
    fun `missing row for a past day within history is NOT_DONE`() {
        val layout = layoutFor(2026, Calendar.JANUARY)
        val today = layout.cells.last().epochDay

        val month = HistoryCalendarStats.dayStates(
            layout = layout,
            slice = CompletionHistorySlice(rows = emptyList(), healedDays = emptySet()),
            todayEpochDay = today,
            earliestEpochDay = layout.cells.first().epochDay,
            isDone = { it.affirmationDone },
        )

        assertEquals(HistoryDayStatus.NOT_DONE, month.days[4].status)
    }

    @Test
    fun `per-habit isDone selects the right flag for the same row`() {
        val layout = layoutFor(2026, Calendar.JANUARY)
        val today = layout.cells.last().epochDay
        val day = layout.cells[4].epochDay

        val slice = CompletionHistorySlice(
            rows = listOf(DailyCompletionEntity(epochDay = day, meditationDone = true, affirmationDone = false)),
            healedDays = emptySet(),
        )

        val meditationMonth = HistoryCalendarStats.dayStates(
            layout = layout, slice = slice, todayEpochDay = today,
            earliestEpochDay = null, isDone = { it.meditationDone },
        )
        val affirmationMonth = HistoryCalendarStats.dayStates(
            layout = layout, slice = slice, todayEpochDay = today,
            earliestEpochDay = null, isDone = { it.affirmationDone },
        )

        assertEquals(HistoryDayStatus.DONE, meditationMonth.days[4].status)
        assertEquals(HistoryDayStatus.NOT_DONE, affirmationMonth.days[4].status)
    }

    @Test
    fun `isToday flag is set only for the day matching todayEpochDay`() {
        val layout = layoutFor(2026, Calendar.JANUARY)
        val today = layout.cells[9].epochDay // day 10

        val month = HistoryCalendarStats.dayStates(
            layout = layout,
            slice = CompletionHistorySlice(rows = emptyList(), healedDays = emptySet()),
            todayEpochDay = today,
            earliestEpochDay = null,
            isDone = { it.affirmationDone },
        )

        assertTrue(month.days[9].isToday)
        assertFalse(month.days[8].isToday)
        assertFalse(month.days[10].isToday)
    }

    @Test
    fun `leadingBlanks is preserved on the resulting HistoryMonth`() {
        val layout = layoutFor(2026, Calendar.JANUARY) // Jan 1 2026 is Thursday -> 4 leading blanks
        val today = layout.cells.last().epochDay

        val month = HistoryCalendarStats.dayStates(
            layout = layout,
            slice = CompletionHistorySlice(rows = emptyList(), healedDays = emptySet()),
            todayEpochDay = today,
            earliestEpochDay = null,
            isDone = { it.affirmationDone },
        )

        assertEquals(4, month.leadingBlanks)
        assertEquals(31, month.days.size)
    }
}
