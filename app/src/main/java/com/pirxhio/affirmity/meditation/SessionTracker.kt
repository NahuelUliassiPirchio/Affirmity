package com.pirxhio.affirmity.meditation

/**
 * What a finished (or exited) guided session reports: [wallElapsedSeconds] includes paused time
 * (analytics), [activeElapsedSeconds] excludes it (the streak-credit gate), and
 * [startWallMillis] is the wall-clock start instant used only for day-of-completion attribution.
 */
data class SessionEndSummary(
    val wallElapsedSeconds: Long,
    val activeElapsedSeconds: Long,
    val startWallMillis: Long,
    val endWallMillis: Long? = null,
    val endZoneId: String? = null,
)

/**
 * Pure owner of every clock a guided session needs: the monotonic wall span, the wall-clock start
 * stamp, and the [ActiveElapsedTimer] that pause freezes. The screen only calls [start], [pause],
 * [resume] and [summary], so the two elapsed values can never be wired to different spans.
 */
class SessionTracker(
    private val monotonicTimeSource: MonotonicTimeSource,
    private val wallClockMillis: () -> Long,
    private val zoneId: () -> String = { java.util.TimeZone.getDefault().id },
) {
    private val activeTimer = ActiveElapsedTimer(monotonicTimeSource)
    private var startedMonotonicMillis: Long? = null
    private var startedWallMillis: Long? = null
    private var finished: SessionEndSummary? = null

    /** Begins (or restarts) the session, resetting both elapsed clocks. */
    fun start() {
        finished = null
        startedMonotonicMillis = monotonicTimeSource.nowMillis()
        startedWallMillis = wallClockMillis()
        activeTimer.start()
    }

    fun pause() = activeTimer.pause()

    fun resume() = activeTimer.resume()

    /** Before [start] this is zero elapsed stamped with the current wall time. */
    fun summary(): SessionEndSummary {
        finished?.let { return it }
        val startMonotonic = startedMonotonicMillis
        val wallElapsedMillis = if (startMonotonic == null) 0L else monotonicTimeSource.nowMillis() - startMonotonic
        return SessionEndSummary(
            wallElapsedSeconds = (wallElapsedMillis / MILLIS_PER_SECOND).coerceAtLeast(0L),
            activeElapsedSeconds = activeTimer.elapsedMillis() / MILLIS_PER_SECOND,
            startWallMillis = startedWallMillis ?: wallClockMillis(),
        )
    }

    fun finish(): SessionEndSummary {
        finished?.let { return it }
        activeTimer.pause()
        return summary().copy(endWallMillis = wallClockMillis(), endZoneId = zoneId()).also { finished = it }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
