package com.pirxhio.affirmity.meditation

import com.pirxhio.affirmity.meditation.breathing.BreathingConfig
import com.pirxhio.affirmity.meditation.breathing.BreathingText
import com.pirxhio.affirmity.meditation.breathing.breathingMeditationDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises [MeditationEngine] purely through [MeditationEvent]s and a [RecordingCommandExecutor]
 * test double — no coroutines, no real clock, no Android. Timer completion is simulated by sending
 * [MeditationEvent.TimerCompleted] with the generation the engine itself dispatched via the last
 * [StartTimer] command, exactly like `TimerCommandExecutor` would from a real clock.
 */
class MeditationEngineTest {

    /** Small config so a "full session" test doesn't need dozens of manual advances. */
    private val config = BreathingConfig(
        rounds = 2,
        breathsPerRound = 2,
        inhaleMillis = 1_000,
        exhaleMillis = 1_000,
        retentionMillis = 1_500,
        recoveryMillis = 500,
    )

    private lateinit var executor: RecordingCommandExecutor
    private lateinit var engine: MeditationEngine

    @Before
    fun setUp() {
        executor = RecordingCommandExecutor()
        engine = MeditationEngine(breathingMeditationDefinition(config), executor)
    }

    private val state get() = engine.state.value

    /** Reads the generation off the most recent [StartTimer] command — mirrors how a real
     * [TimerCommandExecutor] would know what to tag its next clock event with. */
    private fun lastTimerGeneration(): Int =
        (executor.commands.last { it is StartTimer } as StartTimer).generation

    @Test
    fun `start enters the first phase, running its onEnter commands and starting its timer`() {
        engine.send(MeditationEvent.Start)

        assertEquals(SessionStatus.Running, state.status)
        assertEquals(listOf("rounds", "round", "breathing", "breath", "inhale"), state.activePath)
        assertEquals(
            listOf<MeditationCommand>(ShowText(BreathingText.INHALE), StartTimer(1_000, 1)),
            executor.commands,
        )
    }

    @Test
    fun `start is a no-op once the session is already running`() {
        engine.send(MeditationEvent.Start)
        val afterFirstStart = state
        executor.commands.clear()

        engine.send(MeditationEvent.Start)

        assertEquals(afterFirstStart, state)
        assertTrue(executor.commands.isEmpty())
    }

    @Test
    fun `timer completion transitions inhale to exhale`() {
        engine.send(MeditationEvent.Start)
        val generation = lastTimerGeneration()

        engine.send(MeditationEvent.TimerCompleted(generation))

        assertEquals("exhale", state.currentPhaseId)
        assertEquals(listOf("rounds", "round", "breathing", "breath", "exhale"), state.activePath)
    }

    @Test
    fun `multiple breaths advance the breathing repeat's iteration count`() {
        engine.send(MeditationEvent.Start) // breath 0: inhale
        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration())) // breath 0: exhale
        assertEquals(0, state.iterationCounts["breathing"])

        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration())) // breath 1: inhale

        assertEquals("inhale", state.currentPhaseId)
        assertEquals(1, state.iterationCounts["breathing"])
    }

    @Test
    fun `completing all breaths in a round moves to retention, then recovery, then the next round`() {
        engine.send(MeditationEvent.Start)
        // 2 breaths per round = 4 inhale/exhale phases.
        repeat(4) { engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration())) }

        assertEquals("retention", state.currentPhaseId)
        assertEquals(0, state.iterationCounts["rounds"])

        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration()))
        assertEquals("recovery", state.currentPhaseId)

        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration()))
        assertEquals("inhale", state.currentPhaseId)
        assertEquals(1, state.iterationCounts["rounds"])
        // A fresh round restarts its own breathing repeat from 0.
        assertEquals(0, state.iterationCounts["breathing"])
    }

    @Test
    fun `retention ends early on a matching user action instead of waiting for its timer`() {
        engine.send(MeditationEvent.Start)
        repeat(4) { engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration())) }
        assertEquals("retention", state.currentPhaseId)
        val retentionGeneration = lastTimerGeneration()
        assertTrue(executor.commands.any { it == StartLap("retention") })

        engine.send(MeditationEvent.UserAction())

        assertEquals("recovery", state.currentPhaseId)
        assertTrue(executor.commands.any { it == EndLap("retention") })

        // The retention timer firing late (already past, generation stale) must not double-transition.
        engine.send(MeditationEvent.TimerCompleted(retentionGeneration))
        assertEquals("recovery", state.currentPhaseId)
    }

    @Test
    fun `a user action outside an exit-listening phase is ignored`() {
        engine.send(MeditationEvent.Start) // inhale does not listen for UserAction
        val before = state

        engine.send(MeditationEvent.UserAction())

        assertEquals(before, state)
    }

    @Test
    fun `pause stops timer progress and dispatches PauseTimer`() {
        engine.send(MeditationEvent.Start)
        val generation = lastTimerGeneration()

        engine.send(MeditationEvent.Pause)

        assertEquals(SessionStatus.Paused, state.status)
        assertTrue(executor.commands.contains(PauseTimer(generation)))

        // Ticks/completions arriving while paused must not move state.
        engine.send(MeditationEvent.TimerTick(generation, 500, 500))
        assertEquals(0L, state.elapsedInPhaseMillis)
        engine.send(MeditationEvent.TimerCompleted(generation))
        assertEquals("inhale", state.currentPhaseId)
    }

    @Test
    fun `resume dispatches ResumeTimer and lets timer progress affect state again`() {
        engine.send(MeditationEvent.Start)
        val generation = lastTimerGeneration()
        engine.send(MeditationEvent.Pause)

        engine.send(MeditationEvent.Resume)

        assertEquals(SessionStatus.Running, state.status)
        assertTrue(executor.commands.contains(ResumeTimer(generation)))

        engine.send(MeditationEvent.TimerTick(generation, 400, 600))
        assertEquals(400L, state.elapsedInPhaseMillis)
        assertEquals(600L, state.remainingInPhaseMillis)
    }

    @Test
    fun `next manually advances before the timer completes`() {
        engine.send(MeditationEvent.Start)

        engine.send(MeditationEvent.Next)

        assertEquals("exhale", state.currentPhaseId)
    }

    @Test
    fun `a timer completion for a phase already left by a manual advance is ignored`() {
        engine.send(MeditationEvent.Start)
        val staleGeneration = lastTimerGeneration()

        engine.send(MeditationEvent.Next) // now on exhale, with a new generation
        assertEquals("exhale", state.currentPhaseId)

        engine.send(MeditationEvent.TimerCompleted(staleGeneration))

        assertEquals("exhale", state.currentPhaseId)
    }

    @Test
    fun `the same timer completion delivered twice only transitions once`() {
        engine.send(MeditationEvent.Start)
        val generation = lastTimerGeneration()

        engine.send(MeditationEvent.TimerCompleted(generation))
        assertEquals("exhale", state.currentPhaseId)

        engine.send(MeditationEvent.TimerCompleted(generation))

        assertEquals("exhale", state.currentPhaseId)
    }

    @Test
    fun `completing the full definition reaches Completed with an empty active path`() {
        engine.send(MeditationEvent.Start)
        // 2 rounds * (2 breaths * 2 phases + retention + recovery) = 12 phases total.
        repeat(11) { engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration())) }
        assertEquals(SessionStatus.Running, state.status)

        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration()))

        assertEquals(SessionStatus.Completed, state.status)
        assertTrue(state.activePath.isEmpty())
        assertNull(state.currentPhaseId)
    }

    @Test
    fun `events after completion are no-ops`() {
        engine.send(MeditationEvent.Start)
        repeat(12) { engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration())) }
        assertEquals(SessionStatus.Completed, state.status)
        val completed = state
        executor.commands.clear()

        engine.send(MeditationEvent.Next)
        engine.send(MeditationEvent.Pause)
        engine.send(MeditationEvent.TimerCompleted(999))

        assertEquals(completed, state)
        assertTrue(executor.commands.isEmpty())
    }

    @Test
    fun `cancel ends the session from a running state`() {
        engine.send(MeditationEvent.Start)

        engine.send(MeditationEvent.Cancel)

        assertEquals(SessionStatus.Cancelled, state.status)
        assertTrue(state.activePath.isEmpty())

        // No further transitions are possible after cancelling.
        engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration()))
        assertEquals(SessionStatus.Cancelled, state.status)
    }

    @Test
    fun `variables from the definition are exposed on the runtime state from the start`() {
        assertEquals(2, state.variables["rounds"])
        assertEquals(2, state.variables["breathsPerRound"])
    }

    @Test
    fun `starting with a single-round single-breath config still runs retention and recovery once`() {
        val tinyExecutor = RecordingCommandExecutor()
        val tinyEngine = MeditationEngine(
            breathingMeditationDefinition(
                BreathingConfig(rounds = 1, breathsPerRound = 1, inhaleMillis = 1, exhaleMillis = 1, retentionMillis = 1, recoveryMillis = 1),
            ),
            tinyExecutor,
        )
        fun lastGen() = (tinyExecutor.commands.last { it is StartTimer } as StartTimer).generation

        tinyEngine.send(MeditationEvent.Start)
        assertEquals("inhale", tinyEngine.state.value.currentPhaseId)
        tinyEngine.send(MeditationEvent.TimerCompleted(lastGen()))
        assertEquals("exhale", tinyEngine.state.value.currentPhaseId)
        tinyEngine.send(MeditationEvent.TimerCompleted(lastGen()))
        assertEquals("retention", tinyEngine.state.value.currentPhaseId)
        tinyEngine.send(MeditationEvent.TimerCompleted(lastGen()))
        assertEquals("recovery", tinyEngine.state.value.currentPhaseId)
        tinyEngine.send(MeditationEvent.TimerCompleted(lastGen()))
        assertEquals(SessionStatus.Completed, tinyEngine.state.value.status)
    }

    @Test
    fun `natural completion dispatches EndSession with the Completed reason`() {
        engine.send(MeditationEvent.Start)
        repeat(12) { engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration())) }

        assertEquals(SessionStatus.Completed, state.status)
        assertTrue(executor.commands.contains(EndSession(SessionEndReason.Completed)))
    }

    @Test
    fun `cancelling dispatches EndSession with the Cancelled reason and does not run the phase onExit`() {
        engine.send(MeditationEvent.Start)
        repeat(4) { engine.send(MeditationEvent.TimerCompleted(lastTimerGeneration())) }
        assertEquals("retention", state.currentPhaseId)

        engine.send(MeditationEvent.Cancel)

        assertEquals(SessionStatus.Cancelled, state.status)
        assertTrue(executor.commands.contains(EndSession(SessionEndReason.Cancelled)))
        assertFalse(executor.commands.contains(EndLap("retention")))
    }

    @Test
    fun `a fade dispatched by the final phase onExit is followed immediately by EndSession`() {
        val fadeOutDefinition = MeditationDefinition(
            id = "fade-out-demo",
            root = Phase(
                id = "only",
                duration = PhaseDuration.Fixed(1_000L),
                onExit = listOf(StopAmbient(fadeOutMillis = 5_000L)),
            ),
        )
        val fadeExecutor = RecordingCommandExecutor()
        val fadeEngine = MeditationEngine(fadeOutDefinition, fadeExecutor)
        fun lastGen() = (fadeExecutor.commands.last { it is StartTimer } as StartTimer).generation

        fadeEngine.send(MeditationEvent.Start)
        fadeEngine.send(MeditationEvent.TimerCompleted(lastGen()))

        assertEquals(
            listOf<MeditationCommand>(StopAmbient(5_000L), EndSession(SessionEndReason.Completed)),
            fadeExecutor.commands.takeLast(2),
        )
    }

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }
}
