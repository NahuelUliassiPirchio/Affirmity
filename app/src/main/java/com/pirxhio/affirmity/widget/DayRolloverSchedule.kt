package com.pirxhio.affirmity.widget

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
}
