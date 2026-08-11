package com.pirxhio.affirmity.meditation

/**
 * Marker for a side effect a phase declares (on entry/exit) or the engine issues as a structural
 * consequence of a transition (e.g. [StartTimer]). The engine only ever holds and forwards these
 * opaquely to a [MeditationCommandExecutor] — it never interprets them, so adding a new command
 * type (haptics, analytics, ...) never requires touching the engine, only registering a new
 * executor.
 */
interface MeditationCommand

data class PlayAudio(val audioId: String) : MeditationCommand
data class StopAudio(val audioId: String? = null) : MeditationCommand
data class ShowText(val textId: String) : MeditationCommand

/** [durationMillis] null means "no fixed duration" — the timer still runs (so elapsed time is
 * observable) but never emits [MeditationEvent.TimerCompleted]. [generation] lets the executor
 * tag the events it feeds back to the engine — see [MeditationEvent.TimerTick]. */
data class StartTimer(val durationMillis: Long?, val generation: Int) : MeditationCommand
data class PauseTimer(val generation: Int) : MeditationCommand
data class ResumeTimer(val generation: Int) : MeditationCommand

data class StartLap(val lapId: String) : MeditationCommand
data class EndLap(val lapId: String) : MeditationCommand
