package com.pirxhio.affirmity.widget

import com.pirxhio.affirmity.data.DayRiskEvaluator
import java.util.Calendar

/** Pure delay math for [DayRolloverWorker] — next local midnight, plus a 60s safety margin. */
object DayRolloverSchedule {

    /** Millis from [nowMillis] until 60 seconds after the next local midnight. */
    fun delayUntilNextMidnightMillis(nowMillis: Long, calendar: Calendar = Calendar.getInstance()): Long {
        val next = (calendar.clone() as Calendar).apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.SECOND, 60)
        }
        return next.timeInMillis - nowMillis
    }

    /** Seconds past the threshold minute, so the render lands after the evaluator's boundary. */
    private const val THRESHOLD_SLACK_SECONDS = 60

    /**
     * Millis from [nowMillis] until the next local risk threshold (20:00 by default) plus the
     * slack. At or past today's target it rolls to tomorrow's.
     */
    fun delayUntilNextThresholdMillis(
        nowMillis: Long,
        calendar: Calendar = Calendar.getInstance(),
        thresholdMinute: Int = DayRiskEvaluator.DEFAULT_THRESHOLD_MINUTE,
    ): Long {
        val target = (calendar.clone() as Calendar).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, thresholdMinute / 60)
            set(Calendar.MINUTE, thresholdMinute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.SECOND, THRESHOLD_SLACK_SECONDS)
            if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - nowMillis
    }
}
