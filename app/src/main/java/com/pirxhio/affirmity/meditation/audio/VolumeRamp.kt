package com.pirxhio.affirmity.meditation.audio

import com.pirxhio.affirmity.meditation.FadeCurve

/**
 * The arithmetic of one volume ramp, split out from the MediaPlayer driver so it is unit-testable
 * without Android — same split as `ElapsedTimeTracker` vs `RealSessionClock`.
 *
 * Deliberately elapsed-time-based rather than step-counted: exactly like `ElapsedTimeTracker`, a
 * late or dropped tick must not stretch the ramp, because a 5s sleep fade that silently becomes 7s
 * because the device was busy is audible and would break the disposal budget (design §5).
 */
data class VolumeRamp(
    val fromVolume: Float,
    val toVolume: Float,
    val durationMillis: Long,
    val curve: FadeCurve = FadeCurve.Linear,
) {
    fun volumeAt(elapsedMillis: Long): Float {
        if (durationMillis <= 0L) return toVolume
        val progress = (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
        return (fromVolume + (toVolume - fromVolume) * curve.ease(progress)).coerceIn(0f, 1f)
    }

    fun isCompleteAt(elapsedMillis: Long): Boolean =
        durationMillis <= 0L || elapsedMillis >= durationMillis

    /** Remaining ramp when frozen mid-flight by a pause, resumed from the frozen level (design §3.7). */
    fun resumedFrom(volumeAtPause: Float, elapsedAtPause: Long): VolumeRamp =
        copy(
            fromVolume = volumeAtPause,
            durationMillis = (durationMillis - elapsedAtPause).coerceAtLeast(0L),
        )

    companion object {
        /** ~25 volume steps/second. `RealSessionClock`'s 200ms tick is far too coarse for a ramp
         * (5 steps/s is audible zipper noise); 40ms is inaudible and costs one `setVolume` call
         * per tick. */
        const val TICK_MILLIS = 40L
    }
}
