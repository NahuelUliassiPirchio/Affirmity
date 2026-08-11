package com.pirxhio.affirmity.meditation

/**
 * Decides whether a [Repeat] node runs another iteration. The Strategy extension point called out
 * in the architecture: "fixed count" is the only implementation this codebase needs today, but a
 * future "until the user advances" or "until a duration elapses" meditation plugs in here without
 * touching [MeditationEngine] or [Repeat] itself.
 */
fun interface RepetitionStrategy {
    /** Called right after iteration [completedIterationIndex] (0-based) finishes. [context] is the
     * engine's state at that moment, so a future strategy can read e.g. elapsed time or variables.
     * Returns true to run another iteration, false to end the [Repeat]. */
    fun shouldContinue(completedIterationIndex: Int, context: MeditationRuntimeState): Boolean
}

class FixedCountRepetition(private val times: Int) : RepetitionStrategy {
    init {
        require(times > 0) { "times must be > 0, got $times" }
    }

    override fun shouldContinue(completedIterationIndex: Int, context: MeditationRuntimeState): Boolean =
        completedIterationIndex + 1 < times
}
