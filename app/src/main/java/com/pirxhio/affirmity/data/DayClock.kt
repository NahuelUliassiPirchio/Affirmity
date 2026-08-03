package com.pirxhio.affirmity.data

import java.util.Calendar

/**
 * Single source of truth for the app's local-midnight day boundary — used by [AffirmityAppState],
 * the home-screen widget, and its rollover worker so they all agree on what "today" means.
 */
object DayClock {

    /** A device-local day count (arbitrary epoch, only used for day-difference math). */
    fun epochDay(calendar: Calendar = Calendar.getInstance()): Long {
        val midnight = (calendar.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return midnight.timeInMillis / (24 * 60 * 60 * 1000L)
    }

    /** [epochDay] of the day 6 days before [calendar] — start of the rolling last-7-days window. */
    fun rollingWindowStartEpochDay(calendar: Calendar = Calendar.getInstance()): Long =
        epochDay(calendar) - 6

    /** Single-letter weekday labels for the rolling 7-day window ending on [calendar], oldest first. */
    fun rollingWindowDayLetters(calendar: Calendar = Calendar.getInstance()): List<String> {
        val letters = arrayOf("D", "L", "M", "M", "J", "V", "S") // Calendar.DAY_OF_WEEK: Sun=1..Sat=7
        return (6 downTo 0).map { offset ->
            val day = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
            letters[day.get(Calendar.DAY_OF_WEEK) - 1]
        }
    }
}
