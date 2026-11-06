package com.pirxhio.affirmity.ui.streak

import com.pirxhio.affirmity.analytics.DailyGoal
import com.pirxhio.affirmity.analytics.DayRiskDialogVariant
import com.pirxhio.affirmity.data.DayClock
import com.pirxhio.affirmity.data.DayRiskEvaluator
import com.pirxhio.affirmity.data.DayRiskState
import com.pirxhio.affirmity.widget.WidgetBand
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Device-local reading of "now" used to evaluate the day's risk. */
internal data class DayClockReading(val epochDay: Long, val minuteOfDay: Int)

internal fun dayClockReading(nowMillis: Long, zone: ZoneId): DayClockReading {
    val local = Instant.ofEpochMilli(nowMillis).atZone(zone)
    return DayClockReading(
        epochDay = local.toLocalDate().toEpochDay(),
        minuteOfDay = local.hour * 60 + local.minute,
    )
}

/** The dialog shows only when the band is visible and the user has not handled it for [today]. */
internal fun shouldShowDayRiskBanner(band: WidgetBand, dismissedDay: Long?, today: Long): Boolean =
    band.visible && dismissedDay != today

/**
 * Epoch millis of the next moment the banner's decision can change on its own: today's threshold
 * (20:00 by default) while it is still ahead, otherwise the next local midnight. Derived from the
 * zone's rules every time, so DST days (23h/25h) wake at the real boundary.
 */
internal fun nextDayRiskWakeupMillis(
    nowMillis: Long,
    zone: ZoneId,
    thresholdMinute: Int = DayRiskEvaluator.DEFAULT_THRESHOLD_MINUTE,
): Long {
    val local = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val threshold = local.toLocalDate()
        .atTime(LocalTime.of(thresholdMinute / 60, thresholdMinute % 60))
        .atZone(zone).toInstant().toEpochMilli()
    return if (nowMillis < threshold) threshold else nowMillis + DayClock.millisUntilNextMidnight(nowMillis, zone)
}

/** Where the dialog's primary button sends the user. */
enum class DayRiskCtaTarget { MEDITATION, AFFIRMATIONS }

/**
 * The activity the primary button opens: the missing one when a goal is incomplete, otherwise the
 * affirmations feed (the fastest way to keep a streak alive). Null when the dialog does not apply.
 */
internal fun dayRiskCtaTarget(state: DayRiskState): DayRiskCtaTarget? = when (state) {
    is DayRiskState.GoalIncomplete ->
        if (state.missing == DailyGoal.MEDITATION) DayRiskCtaTarget.MEDITATION else DayRiskCtaTarget.AFFIRMATIONS
    DayRiskState.StreakAtRisk -> DayRiskCtaTarget.AFFIRMATIONS
    else -> null
}

internal fun dayRiskDialogVariant(state: DayRiskState): DayRiskDialogVariant? = when (state) {
    is DayRiskState.GoalIncomplete -> DayRiskDialogVariant.GOAL_INCOMPLETE
    DayRiskState.StreakAtRisk -> DayRiskDialogVariant.STREAK_AT_RISK
    else -> null
}
