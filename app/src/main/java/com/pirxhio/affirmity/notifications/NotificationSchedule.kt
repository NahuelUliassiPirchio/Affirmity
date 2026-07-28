package com.pirxhio.affirmity.notifications

import java.util.Calendar
import kotlin.random.Random

/**
 * Pure scheduling math for the self-rescheduling notification chains — no Android dependency
 * beyond [Calendar], so it is fully JVM-unit-testable (see README "Decisions").
 */
object NotificationSchedule {

    /**
     * The first random instant inside `[startMinute, endMinute]` (minutes since midnight) that is
     * strictly after [nowMillis]. If today's window has already elapsed, rolls forward a day —
     * this is what makes a Doze-deferred late fire self-correct onto tomorrow's window instead of
     * needing a special "skip" branch.
     */
    fun nextTriggerAtMillis(
        nowMillis: Long,
        startMinute: Int,
        endMinute: Int,
        random: Random = Random.Default,
    ): Long {
        val span = (endMinute - startMinute).coerceAtLeast(0)
        var dayOffset = 0
        while (true) {
            val pick = startMinute + random.nextInt(span + 1)
            val candidate = midnight(nowMillis, dayOffset) + pick * 60_000L
            if (candidate > nowMillis) return candidate
            dayOffset++
        }
    }

    /** Midnight of the day [dayOffset] days after the day containing [nowMillis]. */
    internal fun midnight(nowMillis: Long, dayOffset: Int): Long =
        Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
