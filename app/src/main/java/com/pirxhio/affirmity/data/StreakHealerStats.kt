package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import java.util.Calendar

/**
 * Pure per-day accessors derived from raw [DailyCompletionEntity] rows only — "layer 1" of the
 * split described in design.md ("Streak Healer v2"). [rawGeneralStreakDays] and [rawBreakEpochDay]
 * are the completions-only view (no healer knowledge); [StreakHealerStats.evaluate] is the
 * healer-aware "layer 2" built on top of it.
 */
data class StreakTimeline(
    val todayEpochDay: Long,
    val startEpochDay: Long,
    val rawGeneralStreakDays: Int,
    val rawBreakEpochDay: Long?,
    val hasActivity: (Long) -> Boolean,
    val isFullDay: (Long) -> Boolean,
)

/** The healer-aware derived state shown to the UI (see design.md's "Interfaces / Contracts"). */
data class StreakHealerState(
    val generalStreakDays: Int,
    val isTodayDone: Boolean,
    val healerCount: Int,
    val pairProgress: Int,
    val healedDays: Set<Long>,
    val activation: HealerActivation,
) {
    val healerHeld: Boolean get() = healerCount > 0
}

/** Whether/how the explicit activation CTA should render for "today". */
sealed interface HealerActivation {
    /** No CTA: either no healer is held, or today is not the single day right after a break. */
    data object Unavailable : HealerActivation

    /** Today == [breakEpochDay] + 1, a healer is held, and it has not been activated yet. */
    data class Available(val breakEpochDay: Long) : HealerActivation

    /** Today's window's break day ([healedEpochDay]) was already activated. */
    data class UsedToday(val healedEpochDay: Long) : HealerActivation
}

/**
 * Derivation of the streak-healer domain (see spec's `streak-healer` and `general-streak`
 * requirements). Two pure layers, no I/O, no `Calendar` reads:
 * - [timeline]: the general streak/break as seen from completions alone.
 * - [evaluate]: applies the persisted activation-event log on top of [timeline].
 */
object StreakHealerStats {

    /** How far back to look when deriving the running streak from `daily_completion`. */
    const val LOOKBACK_DAYS = 370L

    /** This feature's release day — completion history from before it can never retroactively
     * grant/heal (design.md's "Migration / Rollout" decision). */
    val EPOCH_START_DAY: Long = DayClock.epochDay(
        Calendar.getInstance().apply { set(2026, Calendar.AUGUST, 4, 0, 0, 0) }
    )

    /** Floors the usual [LOOKBACK_DAYS] window at [EPOCH_START_DAY] — bounds healer grant/heal/
     * activation eligibility only. Use [rawStreakStartEpochDay] for the visible day-count. */
    fun healerStartEpochDay(todayEpochDay: Long): Long =
        maxOf(todayEpochDay - LOOKBACK_DAYS, EPOCH_START_DAY)

    /** The unfloored [LOOKBACK_DAYS] window, for [evaluate]'s `streakStartEpochDay` — pre-rollout
     * completions still count toward the visible general streak. */
    fun rawStreakStartEpochDay(todayEpochDay: Long): Long =
        todayEpochDay - LOOKBACK_DAYS

    /**
     * Completions-only view: [rawGeneralStreakDays] walks backward from [todayEpochDay], floored at
     * [startEpochDay]; [rawBreakEpochDay] is `todayEpochDay - 1` when that day had zero activity and
     * the day before it was active (or it *is* [startEpochDay]) — i.e. the first zero-activity day
     * after an alive one, ignoring any healer use.
     */
    fun timeline(
        rows: List<DailyCompletionEntity>,
        todayEpochDay: Long,
        startEpochDay: Long,
    ): StreakTimeline {
        val byDay = rows.associateBy { it.epochDay }
        val hasActivity: (Long) -> Boolean =
            { day -> byDay[day]?.let { it.meditationDone || it.affirmationDone } ?: false }
        val isFullDay: (Long) -> Boolean =
            { day -> byDay[day]?.let { it.meditationDone && it.affirmationDone } ?: false }

        var rawStreak = 0
        var day = todayEpochDay
        while (day >= startEpochDay && hasActivity(day)) {
            rawStreak++
            day--
        }

        val breakCandidate = todayEpochDay - 1
        val rawBreakEpochDay = if (
            breakCandidate >= startEpochDay &&
            !hasActivity(breakCandidate) &&
            (breakCandidate == startEpochDay || hasActivity(breakCandidate - 1))
        ) {
            breakCandidate
        } else {
            null
        }

        return StreakTimeline(
            todayEpochDay = todayEpochDay,
            startEpochDay = startEpochDay,
            rawGeneralStreakDays = rawStreak,
            rawBreakEpochDay = rawBreakEpochDay,
            hasActivity = hasActivity,
            isFullDay = isFullDay,
        )
    }

    /**
     * Applies [uses] (the persisted activation-event log, keyed by `healedEpochDay`) on top of
     * [timeline]. A day counts toward the streak if it had activity OR was explicitly healed.
     * `healerCount` is derived by simulating grants (non-overlapping full-day pairs, capped at two) and
     * consumption (any day present in [uses]) from [startEpochDay] through [todayEpochDay] —
     * [startEpochDay] is the *healer* rollout floor and only bounds grant/heal/activation
     * eligibility. [generalStreakDays] is a separate, unfloored day-count: it walks back from
     * [todayEpochDay] to [streakStartEpochDay] (defaulting to [startEpochDay] for callers that
     * don't distinguish the two), so pre-rollout completions still count toward the visible streak
     * even though they can never retroactively grant or heal.
     */
    fun evaluate(
        rows: List<DailyCompletionEntity>,
        uses: List<StreakHealerUseEntity>,
        todayEpochDay: Long,
        startEpochDay: Long,
        streakStartEpochDay: Long = startEpochDay,
    ): StreakHealerState {
        val timeline = timeline(rows, todayEpochDay, startEpochDay)
        val healedDays = uses.map { it.healedEpochDay }.toSet()
        fun effectiveDone(day: Long) = timeline.hasActivity(day) || day in healedDays

        var healerCount = 0
        var pairProgress = 0
        var day = startEpochDay
        while (day <= todayEpochDay) {
            when {
                day in healedDays -> {
                    healerCount = (healerCount - 1).coerceAtLeast(0)
                    pairProgress = 0
                }
                healerCount == 2 -> pairProgress = 0
                timeline.isFullDay(day) -> {
                    pairProgress++
                    if (pairProgress == 2) {
                        healerCount++
                        pairProgress = 0
                    }
                }
                else -> pairProgress = 0
            }
            day++
        }

        // Today not being done yet doesn't zero the streak out — it just isn't counted until it's
        // done. The count shown is "through yesterday" (or through today once it's completed).
        val isTodayDone = effectiveDone(todayEpochDay)
        var generalStreakDays = 0
        var d = if (isTodayDone) todayEpochDay else todayEpochDay - 1
        while (d >= streakStartEpochDay && effectiveDone(d)) {
            generalStreakDays++
            d--
        }

        val breakCandidate = todayEpochDay - 1
        val isBreakDay = breakCandidate >= startEpochDay &&
            !timeline.hasActivity(breakCandidate) &&
            (breakCandidate == startEpochDay || effectiveDone(breakCandidate - 1))

        val activation: HealerActivation = when {
            isBreakDay && breakCandidate in healedDays -> HealerActivation.UsedToday(breakCandidate)
            isBreakDay && healerCount > 0 -> HealerActivation.Available(breakCandidate)
            else -> HealerActivation.Unavailable
        }

        return StreakHealerState(
            generalStreakDays = generalStreakDays,
            isTodayDone = isTodayDone,
            healerCount = healerCount,
            pairProgress = pairProgress,
            healedDays = healedDays,
            activation = activation,
        )
    }
}
