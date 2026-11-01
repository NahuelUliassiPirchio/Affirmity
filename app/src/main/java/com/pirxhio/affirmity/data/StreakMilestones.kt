package com.pirxhio.affirmity.data

/**
 * Celebration milestones for the healer-aware general streak (in days).
 *
 * Note: [StreakHealerStats.LOOKBACK_DAYS] is 370, so 365 sits near the derivable cap; anything
 * beyond ~370 days cannot be observed from `daily_completion`.
 */
object StreakMilestones {
    val ALL: List<Int> = listOf(1, 3, 7, 14, 30, 60, 100, 180, 365)

    /** The milestone equal to [streakDays], or null when it is not an exact hit. */
    fun milestoneReachedAt(streakDays: Int): Int? = ALL.firstOrNull { it == streakDays }

    /** The largest milestone <= [streakDays], or null if none reached yet. */
    fun highestReached(streakDays: Int): Int? = ALL.lastOrNull { it <= streakDays }

    /** The smallest milestone > [streakDays], or null past the last one. */
    fun next(streakDays: Int): Int? = ALL.firstOrNull { it > streakDays }
}
