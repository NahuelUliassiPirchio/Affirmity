package com.pirxhio.affirmity.meditation

/**
 * Semantic events the [MeditationEngine] reacts to. The UI (or a [SessionClock] bridge) sends
 * these; what an event actually *means* is decided by the current phase, never by the sender —
 * e.g. [UserAction] only ends the active phase if that phase declared it as an exit trigger.
 */
sealed interface MeditationEvent {
    data object Start : MeditationEvent
    data object Pause : MeditationEvent
    data object Resume : MeditationEvent

    /** Manually ends the current phase regardless of its timer/exit conditions. */
    data object Next : MeditationEvent

    /** Distinct from [Next] so a future definition can give it different semantics (e.g. skip to
     * the end of the enclosing sequence) without changing the engine's event vocabulary — for now
     * the engine treats both identically. */
    data object Skip : MeditationEvent

    /** Ends the session early, from any non-terminal status. */
    data object Cancel : MeditationEvent

    /** A user-triggered domain action (e.g. "release the breath"). [actionId] lets a future phase
     * discriminate between distinct actions; unused by the current breathing definition. */
    data class UserAction(val actionId: String = "default") : MeditationEvent

    data class AudioCompleted(val audioId: String) : MeditationEvent

    /** Progress ticks from the timer bridge, tagged with [generation] so a tick from a timer the
     * engine already moved past (phase changed) is ignored instead of corrupting the new phase's
     * progress. */
    data class TimerTick(val generation: Int, val elapsedMillis: Long, val remainingMillis: Long?) :
        MeditationEvent

    /** Same staleness guard as [TimerTick] — see [MeditationRuntimeState.timerGeneration]. */
    data class TimerCompleted(val generation: Int) : MeditationEvent
}
