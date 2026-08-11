package com.pirxhio.affirmity.meditation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A monotonic millisecond source, injected so time is fakeable in tests — never `System.
 * currentTimeMillis()` directly, which can jump on a wall-clock/timezone change. The real Android
 * implementation (`SystemClock.elapsedRealtime`) is wired from the `ui.meditation` layer, keeping
 * this package free of Android imports. */
fun interface MonotonicTimeSource {
    fun nowMillis(): Long
}

sealed interface ClockEvent {
    data class Tick(val elapsedMillis: Long, val remainingMillis: Long?) : ClockEvent
    data object Completed : ClockEvent
}

/**
 * The one place session timing lives — no [Phase]/command/meditation is allowed to create its own
 * timer. A single instance is shared for the whole session; [start] always fully (re)establishes
 * it, so there is no orphaned-timer state to reason about between phases.
 */
interface SessionClock {
    val events: Flow<ClockEvent>

    /** [durationMillis] null means "count up, never complete" (a [PhaseDuration.Manual] phase). */
    fun start(durationMillis: Long?)
    fun pause()
    fun resume()
    fun stop()
}

/**
 * Pure elapsed/remaining bookkeeping, split out from [RealSessionClock] so it's unit-testable
 * without coroutines. Deliberately timestamp-based, not tick-counted: if the process is
 * backgrounded and ticks are delayed or dropped, [elapsedMillis] is still correct the moment
 * ticking resumes, because it is always `now - startedAt`, never `ticksSeen * interval`.
 */
internal class ElapsedTimeTracker(private val timeSource: MonotonicTimeSource) {
    private var durationMillis: Long? = null
    private var startedAtMillis: Long = 0L
    private var accumulatedMillis: Long = 0L
    private var running: Boolean = false

    fun start(durationMillis: Long?) {
        this.durationMillis = durationMillis
        accumulatedMillis = 0L
        startedAtMillis = timeSource.nowMillis()
        running = true
    }

    fun pause() {
        if (!running) return
        accumulatedMillis = elapsedMillis()
        running = false
    }

    fun resume() {
        if (running) return
        startedAtMillis = timeSource.nowMillis()
        running = true
    }

    fun elapsedMillis(): Long =
        if (running) accumulatedMillis + (timeSource.nowMillis() - startedAtMillis) else accumulatedMillis

    fun remainingMillis(): Long? = durationMillis?.let { (it - elapsedMillis()).coerceAtLeast(0L) }

    fun isCompleted(): Boolean = durationMillis?.let { elapsedMillis() >= it } ?: false
}

class RealSessionClock(
    private val scope: CoroutineScope,
    private val timeSource: MonotonicTimeSource,
    private val tickIntervalMillis: Long = 200L,
) : SessionClock {
    private val tracker = ElapsedTimeTracker(timeSource)
    private val _events = MutableSharedFlow<ClockEvent>(extraBufferCapacity = 8)
    override val events: Flow<ClockEvent> = _events.asSharedFlow()
    private var tickJob: Job? = null

    override fun start(durationMillis: Long?) {
        tickJob?.cancel()
        tracker.start(durationMillis)
        runTickLoop()
    }

    override fun pause() {
        tickJob?.cancel()
        tracker.pause()
    }

    override fun resume() {
        tracker.resume()
        runTickLoop()
    }

    override fun stop() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun runTickLoop() {
        tickJob = scope.launch {
            while (isActive) {
                _events.emit(ClockEvent.Tick(tracker.elapsedMillis(), tracker.remainingMillis()))
                if (tracker.isCompleted()) {
                    _events.emit(ClockEvent.Completed)
                    break
                }
                delay(tickIntervalMillis)
            }
        }
    }
}
