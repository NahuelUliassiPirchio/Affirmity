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

    /** [epochDay] of the Monday starting the calendar week containing [calendar]. */
    fun weekStartEpochDay(calendar: Calendar = Calendar.getInstance()): Long {
        val daysSinceMonday = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val monday = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -daysSinceMonday) }
        return epochDay(monday)
    }
}
