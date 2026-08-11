package com.pirxhio.affirmity.meditation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Interprets [MeditationCommand]s as concrete side effects. The engine never implements this
 * itself — see [CompositeCommandExecutor] for how independent effects (timer, text, laps, audio,
 * and eventually haptics/analytics) are composed without touching each other or the engine. */
interface MeditationCommandExecutor {
    fun execute(command: MeditationCommand)
}

/** Broadcasts every command to all [executors]; each one ignores commands it doesn't recognize.
 * Adding a new side effect (e.g. haptics) is adding a new executor to this list — nothing else
 * changes. */
class CompositeCommandExecutor(
    private val executors: List<MeditationCommandExecutor>,
) : MeditationCommandExecutor {
    override fun execute(command: MeditationCommand) {
        executors.forEach { it.execute(command) }
    }
}

/**
 * Bridges timer commands to a [SessionClock] and feeds its events back into the engine via
 * [sendEvent]. This is the only place the engine's timing intent ([StartTimer]/[PauseTimer]/
 * [ResumeTimer]) turns into an actual clock — the engine itself never references [SessionClock].
 *
 * Takes a plain `(MeditationEvent) -> Unit` rather than a [MeditationEngine] reference: the
 * engine and this executor are built in the same wiring step and would otherwise need each other
 * before either exists — a lambda breaks that cycle (it can close over a not-yet-initialized
 * `lateinit var engine` and only actually call it later, once the clock emits its first event).
 */
class TimerCommandExecutor(
    private val clock: SessionClock,
    private val sendEvent: (MeditationEvent) -> Unit,
    scope: CoroutineScope,
) : MeditationCommandExecutor {
    @Volatile
    private var currentGeneration: Int = 0

    init {
        scope.launch {
            clock.events.collect { event ->
                when (event) {
                    is ClockEvent.Tick ->
                        sendEvent(MeditationEvent.TimerTick(currentGeneration, event.elapsedMillis, event.remainingMillis))
                    ClockEvent.Completed ->
                        sendEvent(MeditationEvent.TimerCompleted(currentGeneration))
                }
            }
        }
    }

    override fun execute(command: MeditationCommand) {
        when (command) {
            is StartTimer -> {
                currentGeneration = command.generation
                clock.start(command.durationMillis)
            }
            is PauseTimer -> clock.pause()
            is ResumeTimer -> clock.resume()
            else -> Unit
        }
    }
}

/** Exposes the id of the last [ShowText] command as UI-observable state — the domain only ever
 * says "show this text id"; resolving it to an actual string is the UI's job. */
class TextDisplayCommandExecutor : MeditationCommandExecutor {
    private val _currentTextId = MutableStateFlow<String?>(null)
    val currentTextId: StateFlow<String?> = _currentTextId.asStateFlow()

    override fun execute(command: MeditationCommand) {
        if (command is ShowText) _currentTextId.value = command.textId
    }
}

data class LapRecord(val lapId: String, val startedAtMillis: Long, val endedAtMillis: Long? = null)

/** Turns [StartLap]/[EndLap] commands into a queryable log — e.g. how long the user actually held
 * a retention phase. */
class LapTrackerCommandExecutor(
    private val timeSource: MonotonicTimeSource,
) : MeditationCommandExecutor {
    private val _laps = MutableStateFlow<List<LapRecord>>(emptyList())
    val laps: StateFlow<List<LapRecord>> = _laps.asStateFlow()

    override fun execute(command: MeditationCommand) {
        when (command) {
            is StartLap -> _laps.value = _laps.value + LapRecord(command.lapId, timeSource.nowMillis())
            is EndLap -> _laps.value = _laps.value.map {
                if (it.lapId == command.lapId && it.endedAtMillis == null) {
                    it.copy(endedAtMillis = timeSource.nowMillis())
                } else {
                    it
                }
            }
            else -> Unit
        }
    }
}
