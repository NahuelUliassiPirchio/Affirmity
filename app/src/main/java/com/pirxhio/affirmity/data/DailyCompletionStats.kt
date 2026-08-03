package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.DailyCompletionEntity

/**
 * Pure derivation of [WeeklyStreak] shapes from raw [DailyCompletionEntity] rows — no I/O, no
 * `Calendar` reads. Each day is independent: a missed day never erases an earlier completed day
 * in the same window (this is the fix for the old contiguous-streak-window bug).
 */
object DailyCompletionStats {

    /**
     * Completion flags for the rolling 7-day window starting at [weekStartEpochDay] (oldest day
     * first, today last), plus the current contiguous streak of [isDone] days ending on
     * [todayEpochDay]. A day with no matching row in [rows] counts as not done.
     */
    fun toWeeklyStreak(
        rows: List<DailyCompletionEntity>,
        weekStartEpochDay: Long,
        todayEpochDay: Long,
        isDone: (DailyCompletionEntity) -> Boolean,
    ): WeeklyStreak {
        val byDay = rows.associateBy { it.epochDay }
        val completedDays = (0 until 7).map { offset ->
            byDay[weekStartEpochDay + offset]?.let(isDone) ?: false
        }
        return WeeklyStreak(
            completedDays = completedDays,
            streakDays = streakOf(rows, todayEpochDay, isDone),
        )
    }

    /** Consecutive count of [isDone] days ending at [todayEpochDay] (walking backwards day by day). */
    fun streakOf(
        rows: List<DailyCompletionEntity>,
        todayEpochDay: Long,
        isDone: (DailyCompletionEntity) -> Boolean,
    ): Int {
        val byDay = rows.associateBy { it.epochDay }
        var streak = 0
        var day = todayEpochDay
        while (byDay[day]?.let(isDone) == true) {
            streak++
            day--
        }
        return streak
    }
}
