package com.pirxhio.affirmity.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar

/**
 * Single source of truth for the app's local-midnight day boundary — used by [AffirmityAppState],
 * the home-screen widget, and its rollover worker so they all agree on what "today" means.
 */
object DayClock {

    /**
     * The local calendar date's days since 1970-01-01 (`LocalDate.toEpochDay()`), read from
     * [calendar]'s own time zone. Injective per calendar date in every zone, DST included; mirrors
     * the server's `utcMillisToLocalEpochDay`.
     */
    fun epochDay(calendar: Calendar = Calendar.getInstance()): Long =
        LocalDate.of(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH),
        ).toEpochDay()

    /** [epochDay] of the local date that [millis] falls on in [zone]. */
    fun epochDay(zone: ZoneId, millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay()

    /** [epochDay] of the day 6 days before [calendar] — start of the rolling last-7-days window. */
    fun rollingWindowStartEpochDay(calendar: Calendar = Calendar.getInstance()): Long =
        epochDay(calendar) - 6

    /**
     * Inverse of [epochDay]: a [Calendar] (in [calendar]'s time zone) set to local midnight of the
     * calendar date identified by [epochDay], via [LocalDate.ofEpochDay]. Calendar's lenient
     * fields resolve a skipped midnight (DST gap) to the first valid instant of that date.
     */
    fun calendarForEpochDay(epochDay: Long, calendar: Calendar = Calendar.getInstance()): Calendar {
        val date = LocalDate.ofEpochDay(epochDay)
        return (calendar.clone() as Calendar).apply {
            clear()
            set(date.year, date.monthValue - 1, date.dayOfMonth, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    /**
     * [epochDay] of whichever calendar day held more of the wall-clock span
     * `[startMillis, endMillis)` — used to attribute a session that crosses local midnight (e.g.
     * started 23:57, ran 10 minutes) to the day it was mostly spent on, not just the day it ended.
     * Walks day-by-day with [Calendar.add] rather than multiplying an epoch-day index by a fixed
     * millis-per-day, so it stays correct across a DST boundary.
     */
    fun attributedEpochDay(startMillis: Long, endMillis: Long, calendar: Calendar = Calendar.getInstance()): Long {
        // A wall clock stepped backward mid-span (end < start) has no measurable overlap: use the end day.
        if (endMillis < startMillis) return epochDay((calendar.clone() as Calendar).apply { timeInMillis = endMillis })
        val startDay = epochDay((calendar.clone() as Calendar).apply { timeInMillis = startMillis })
        val endDay = epochDay((calendar.clone() as Calendar).apply { timeInMillis = endMillis })
        if (startDay == endDay) return startDay

        val dayStart = (calendar.clone() as Calendar).apply {
            timeInMillis = startMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var bestDay = startDay
        var bestOverlapMillis = -1L
        var day = startDay
        while (day <= endDay) {
            val boundaryStart = dayStart.timeInMillis
            val boundaryEnd = (dayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            val overlapMillis = (minOf(endMillis, boundaryEnd) - maxOf(startMillis, boundaryStart)).coerceAtLeast(0L)
            if (overlapMillis > bestOverlapMillis) {
                bestOverlapMillis = overlapMillis
                bestDay = day
            }
            dayStart.add(Calendar.DAY_OF_YEAR, 1)
            day++
        }
        return bestDay
    }

    /**
     * Single-letter weekday labels for the rolling 7-day window ending on [calendar], oldest first.
     * [letters] must be a 7-item array ordered `Calendar.DAY_OF_WEEK`: Sun=1..Sat=7 — callers
     * resolve it from a locale-aware resource (e.g. `R.array.weekday_letters`) per call, never a
     * cached/hardcoded value, so this stays a pure, Context-free JVM function.
     */
    fun rollingWindowDayLetters(letters: List<String>, calendar: Calendar = Calendar.getInstance()): List<String> {
        require(letters.size == 7) { "letters must have exactly 7 items (Sun..Sat), got ${letters.size}" }
        return (6 downTo 0).map { offset ->
            val day = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
            letters[day.get(Calendar.DAY_OF_WEEK) - 1]
        }
    }
}
