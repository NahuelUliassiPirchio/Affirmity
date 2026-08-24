package com.pirxhio.affirmity.meditation.audio

import com.pirxhio.affirmity.meditation.SessionEndReason

/** What the executor should do with one piece of currently-playing audio when a session ends. */
sealed interface TerminalAudioAction {
    data class FadeOut(val audioId: String, val durationMillis: Long) : TerminalAudioAction
    data class StopNow(val audioId: String) : TerminalAudioAction
}

/**
 * Pure, Android-free terminal-cleanup planner (design §2.2) — the highest-risk interaction this
 * spec introduces (an in-flight authored fade racing the engine's own `EndSession` broadcast) is
 * unit-testable here with plain JUnit, no Android.
 */
object TerminalAudioPolicy {

    /** Short courtesy fade applied to anything still audible at a graceful end that the definition
     * did not already ramp — long enough to avoid a click, short enough to finish before the
     * completion screen can be navigated away from (design §5). */
    const val GRACEFUL_END_FADE_MILLIS = 400L

    fun plan(
        reason: SessionEndReason,
        playingAudioIds: Set<String>,
        /** Ids with a ramp already in flight whose target volume is exactly 0f — i.e. already on
         * their way to silence and already scheduled to self-stop. */
        rampingToSilenceAudioIds: Set<String>,
        gracefulFadeMillis: Long = GRACEFUL_END_FADE_MILLIS,
    ): List<TerminalAudioAction> = when (reason) {
        // The user asked for it to stop. Stop. Cancels in-flight ramps too.
        SessionEndReason.Cancelled ->
            playingAudioIds.sorted().map { TerminalAudioAction.StopNow(it) }

        // Graceful: an authored fade-to-silence is left strictly alone; everything else still
        // audible (including a bed left ducked by a voice that never restored) gets the courtesy
        // fade so nothing survives the session.
        SessionEndReason.Completed ->
            (playingAudioIds - rampingToSilenceAudioIds).sorted()
                .map { TerminalAudioAction.FadeOut(it, gracefulFadeMillis) }
    }
}
