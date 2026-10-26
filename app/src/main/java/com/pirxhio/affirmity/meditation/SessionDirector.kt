package com.pirxhio.affirmity.meditation

/**
 * A command executor that also needs to see the raw [MeditationEvent] stream (ticks, audio
 * completions) before the engine filters it -- e.g. the Wim Hof voice guide. Commands arrive via
 * [execute] like any other executor; events arrive via [onEvent] from a tap on the `sendEvent`
 * lambdas, always BEFORE they reach the engine. Main-thread confined, like the rest of the session.
 */
interface SessionDirector : MeditationCommandExecutor {
    fun onEvent(event: MeditationEvent)
}

/** Builds a director that emits its own commands into [sink] and reads engine state through the provider. */
typealias SessionDirectorFactory = (sink: MeditationCommandExecutor, state: () -> MeditationRuntimeState) -> SessionDirector
