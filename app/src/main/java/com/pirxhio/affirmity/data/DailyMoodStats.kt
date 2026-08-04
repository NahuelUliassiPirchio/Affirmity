package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.DailyMoodEntity

/** Mood levels are always 1..5 (😔🙁😐🙂✨) — see [DailyMoodEntity.moodValue]. */
const val MOOD_MIN = 1
const val MOOD_MAX = 5

/** How many of each mood value (1..5, index 0 = value 1) were logged in a window, as a percentage
 * of the logged days (0 when nothing was logged that day). */
data class MoodDistribution(val percentByValue: List<Int> = List(MOOD_MAX) { 0 })

/** A single point on the trend chart: the day's mood value, or `null` if nothing was logged. */
data class MoodTrendPoint(val epochDay: Long, val moodValue: Int?)

/**
 * Pure derivation of mood summaries from raw [DailyMoodEntity] rows — no I/O, no `Calendar` reads.
 * Mirrors [DailyCompletionStats]'s style: everything here is a function of the rows passed in.
 */
object DailyMoodStats {

    /** Mean of logged mood values in [rows], rounded to one decimal; `null` if nothing was logged. */
    fun average(rows: List<DailyMoodEntity>): Double? =
        if (rows.isEmpty()) null else rows.sumOf { it.moodValue }.toDouble() / rows.size

    /** Percentage of logged days at each mood value 1..5, summing to 100 (or all-zero if empty). */
    fun distribution(rows: List<DailyMoodEntity>): MoodDistribution {
        if (rows.isEmpty()) return MoodDistribution()
        val counts = IntArray(MOOD_MAX)
        rows.forEach { entity ->
            val index = entity.moodValue - 1
            if (index in counts.indices) counts[index]++
        }
        val percents = counts.map { count -> (count * 100) / rows.size }
        return MoodDistribution(percentByValue = percents)
    }

    /** One point per day in `[startEpochDay, startEpochDay + days - 1]`, oldest first. */
    fun trend(rows: List<DailyMoodEntity>, startEpochDay: Long, days: Int = 7): List<MoodTrendPoint> {
        val byDay = rows.associateBy { it.epochDay }
        return (0 until days).map { offset ->
            val day = startEpochDay + offset
            MoodTrendPoint(epochDay = day, moodValue = byDay[day]?.moodValue)
        }
    }

    /** Consecutive count of logged days ending at [todayEpochDay] (walking backwards day by day). */
    fun streakOf(rows: List<DailyMoodEntity>, todayEpochDay: Long): Int {
        val byDay = rows.associateBy { it.epochDay }
        var streak = 0
        var day = todayEpochDay
        while (byDay.containsKey(day)) {
            streak++
            day--
        }
        return streak
    }
}
