package com.pirxhio.affirmity.meditation

import java.util.ArrayDeque
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single, meditation-agnostic interpreter: it walks a [MeditationDefinition] tree and reacts
 * to [MeditationEvent]s, dispatching [MeditationCommand]s to [commandExecutor] as a side effect of
 * each transition. It never depends on a clock, on audio, or on any concrete meditation type —
 * see [SessionClock]/[TimerCommandExecutor] for how timing is bridged in as "just another command".
 *
 * [send] is synchronous and serialized with a monitor-protected event queue rather than a
 * coroutine channel/actor: every transition is a pure, non-suspending computation. The queue also
 * defers events emitted reentrantly by command executors until the current transition has finished,
 * while keeping the engine testable with plain JUnit — no coroutine test dispatcher needed (see
 * MeditationEngineTest).
 */
class MeditationEngine(
    private val definition: MeditationDefinition,
    private val commandExecutor: MeditationCommandExecutor,
) {
    private val lock = Any()
    private val pendingEvents = ArrayDeque<MeditationEvent>()
    private var isProcessingEvents = false

    /** Root-to-parent path of the active [Phase]; null id path segments never happen because
     * [descend] always terminates at a [Phase]. Empty when no session is in flight. */
    private var frames: List<Frame> = emptyList()
    private var currentPhase: Phase? = null

    private val _state = MutableStateFlow(MeditationRuntimeState(variables = definition.variables))
    val state: StateFlow<MeditationRuntimeState> = _state.asStateFlow()

    fun send(event: MeditationEvent) {
        synchronized(lock) {
            pendingEvents.addLast(event)
            if (isProcessingEvents) return

            isProcessingEvents = true
            try {
                while (pendingEvents.isNotEmpty()) {
                    processEvent(pendingEvents.removeFirst())
                }
            } finally {
                isProcessingEvents = false
            }
        }
    }

    private fun processEvent(event: MeditationEvent) {
        val current = _state.value
        when (event) {
            MeditationEvent.Start -> {
                if (current.status != SessionStatus.Idle) return
                enterInitialPhase()
            }

            MeditationEvent.Pause -> {
                if (current.status != SessionStatus.Running) return
                dispatch(PauseTimer(current.timerGeneration))
                _state.value = current.copy(status = SessionStatus.Paused)
            }

            MeditationEvent.Resume -> {
                if (current.status != SessionStatus.Paused) return
                dispatch(ResumeTimer(current.timerGeneration))
                _state.value = current.copy(status = SessionStatus.Running)
            }

            MeditationEvent.Next, MeditationEvent.Skip -> {
                if (current.status != SessionStatus.Running && current.status != SessionStatus.Paused) return
                exitCurrentPhaseAndAdvance()
            }

            MeditationEvent.Cancel -> {
                if (current.status == SessionStatus.Completed || current.status == SessionStatus.Cancelled) return
                frames = emptyList()
                currentPhase = null
                _state.value = current.copy(
                    status = SessionStatus.Cancelled,
                    activePath = emptyList(),
                    remainingInPhaseMillis = null,
                )
                dispatch(EndSession(SessionEndReason.Cancelled))
            }

            is MeditationEvent.UserAction -> handlePhaseExitEvent(current, MeditationEvent.UserAction::class)
            is MeditationEvent.AudioCompleted -> handlePhaseExitEvent(current, MeditationEvent.AudioCompleted::class)

            is MeditationEvent.TimerTick -> {
                if (current.status != SessionStatus.Running) return
                if (event.generation != current.timerGeneration) return
                _state.value = current.copy(
                    elapsedInPhaseMillis = event.elapsedMillis,
                    remainingInPhaseMillis = event.remainingMillis,
                )
            }

            is MeditationEvent.TimerCompleted -> {
                if (current.status != SessionStatus.Running) return
                if (event.generation != current.timerGeneration) return
                val phase = currentPhase ?: return
                if (phase.duration is PhaseDuration.Fixed) {
                    exitCurrentPhaseAndAdvance()
                }
            }
        }
    }

    private fun handlePhaseExitEvent(current: MeditationRuntimeState, eventType: kotlin.reflect.KClass<out MeditationEvent>) {
        if (current.status != SessionStatus.Running) return
        val phase = currentPhase ?: return
        if (eventType in phase.exitEvents) {
            exitCurrentPhaseAndAdvance()
        }
    }

    private fun enterInitialPhase() {
        val (newFrames, leaf) = descend(definition.root, emptyList())
        frames = newFrames
        currentPhase = leaf
        val baseState = _state.value.copy(
            status = SessionStatus.Running,
            activePath = pathOf(newFrames, leaf),
            iterationCounts = iterationCountsOf(newFrames),
            elapsedInPhaseMillis = 0L,
            remainingInPhaseMillis = null,
        )
        enterPhase(leaf, baseState)
    }

    private fun exitCurrentPhaseAndAdvance() {
        val phase = currentPhase ?: return
        phase.onExit.forEach(::dispatch)
        when (val result = advance(frames, _state.value)) {
            is AdvanceResult.EnterPhase -> {
                frames = result.frames
                currentPhase = result.phase
                val baseState = _state.value.copy(
                    activePath = pathOf(result.frames, result.phase),
                    iterationCounts = iterationCountsOf(result.frames),
                    elapsedInPhaseMillis = 0L,
                    remainingInPhaseMillis = null,
                )
                enterPhase(result.phase, baseState)
            }

            AdvanceResult.SessionCompleted -> {
                frames = emptyList()
                currentPhase = null
                _state.value = _state.value.copy(
                    status = SessionStatus.Completed,
                    activePath = emptyList(),
                    elapsedInPhaseMillis = 0L,
                    remainingInPhaseMillis = null,
                )
                dispatch(EndSession(SessionEndReason.Completed))
            }
        }
    }

    /** Commits [baseState] (already carrying the new [activePath]/[iterationCounts]) with a bumped
     * [MeditationRuntimeState.timerGeneration], then runs the phase's onEnter commands and starts
     * its timer — in that order, so a StartTimer completion can never race a not-yet-dispatched
     * onEnter command. */
    private fun enterPhase(phase: Phase, baseState: MeditationRuntimeState) {
        val generation = baseState.timerGeneration + 1
        _state.value = baseState.copy(timerGeneration = generation)
        phase.onEnter.forEach(::dispatch)
        val durationMillis = when (val duration = phase.duration) {
            is PhaseDuration.Fixed -> duration.millis
            PhaseDuration.Manual -> null
        }
        dispatch(StartTimer(durationMillis, generation))
    }

    private fun dispatch(command: MeditationCommand) = commandExecutor.execute(command)

    private fun pathOf(frames: List<Frame>, leaf: Phase): List<String> =
        frames.map { it.node.id } + leaf.id

    private fun iterationCountsOf(frames: List<Frame>): Map<String, Int> =
        frames.filterIsInstance<Frame.InRepeat>().associate { it.node.id to it.iteration }

    /** One level of the active path above the current [Phase]: either "which child of a
     * [MeditationSequence]" or "which iteration of a [Repeat]". */
    private sealed interface Frame {
        val node: MeditationNode

        data class InSequence(override val node: MeditationSequence, val childIndex: Int) : Frame
        data class InRepeat(override val node: Repeat, val iteration: Int) : Frame
    }

    private sealed interface AdvanceResult {
        data class EnterPhase(val frames: List<Frame>, val phase: Phase) : AdvanceResult
        data object SessionCompleted : AdvanceResult
    }

    /** Walks down from [node] to its first [Phase], pushing a [Frame] for every [MeditationSequence]
     * /[Repeat] passed through. */
    private fun descend(node: MeditationNode, frames: List<Frame>): Pair<List<Frame>, Phase> =
        when (node) {
            is Phase -> frames to node
            is MeditationSequence -> descend(node.children[0], frames + Frame.InSequence(node, 0))
            is Repeat -> descend(node.child, frames + Frame.InRepeat(node, 0))
        }

    /** Pops the innermost [Frame] and decides what runs next: the next sibling in a [MeditationSequence],
     * another iteration of a [Repeat] (per its [RepetitionStrategy]), or — if [frames] is empty —
     * the whole session is done. Bubbles up (recurses on the parent frame) when a container is
     * exhausted, exactly like returning from a finished loop/sequence in a normal tree walk. */
    private fun advance(frames: List<Frame>, state: MeditationRuntimeState): AdvanceResult {
        if (frames.isEmpty()) return AdvanceResult.SessionCompleted
        val innermost = frames.last()
        val outer = frames.dropLast(1)
        return when (innermost) {
            is Frame.InSequence -> {
                val nextIndex = innermost.childIndex + 1
                if (nextIndex < innermost.node.children.size) {
                    val (newFrames, leaf) =
                        descend(innermost.node.children[nextIndex], outer + innermost.copy(childIndex = nextIndex))
                    AdvanceResult.EnterPhase(newFrames, leaf)
                } else {
                    advance(outer, state)
                }
            }

            is Frame.InRepeat -> {
                if (innermost.node.strategy.shouldContinue(innermost.iteration, state)) {
                    val nextIteration = innermost.iteration + 1
                    val (newFrames, leaf) =
                        descend(innermost.node.child, outer + innermost.copy(iteration = nextIteration))
                    AdvanceResult.EnterPhase(newFrames, leaf)
                } else {
                    advance(outer, state)
                }
            }
        }
    }
}
