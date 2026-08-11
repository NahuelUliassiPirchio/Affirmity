package com.pirxhio.affirmity.meditation

enum class SessionStatus { Idle, Running, Paused, Completed, Cancelled }

/**
 * Runtime state of one session — everything that is *not* part of the static [MeditationDefinition]
 * tree. Indices like "which round/breath we're on" deliberately never become distinct engine
 * states; they live here in [iterationCounts], keyed by the [Repeat] node's id.
 */
data class MeditationRuntimeState(
    val status: SessionStatus = SessionStatus.Idle,
    /** Node ids from the definition's root down to the active [Phase], e.g.
     * `["rounds", "round", "breathing", "breath", "inhale"]`. Empty when [status] is [SessionStatus
     * .Idle], [SessionStatus.Completed] or [SessionStatus.Cancelled]. */
    val activePath: List<String> = emptyList(),
    /** [Repeat] node id -> current 0-based iteration. */
    val iterationCounts: Map<String, Int> = emptyMap(),
    /** The meditation's configurable inputs, carried from [MeditationDefinition.variables]. */
    val variables: Map<String, Any?> = emptyMap(),
    val elapsedInPhaseMillis: Long = 0L,
    val remainingInPhaseMillis: Long? = null,
    /** Bumped every time a new phase is entered — see [MeditationEvent.TimerTick]'s staleness
     * guard. Not meaningful UI state, only used internally and in tests. */
    val timerGeneration: Int = 0,
) {
    val currentPhaseId: String? get() = activePath.lastOrNull()
}
