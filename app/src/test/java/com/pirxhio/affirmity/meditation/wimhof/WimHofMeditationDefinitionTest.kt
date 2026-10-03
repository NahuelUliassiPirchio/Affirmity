package com.pirxhio.affirmity.meditation.wimhof

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WimHofMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    private fun run(config: WimHofConfig): Pair<Int, List<MeditationCommand>> {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(wimHofMeditationDefinition(config), executor)
        engine.send(MeditationEvent.Start)
        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }
        assertEquals(SessionStatus.Completed, engine.state.value.status)
        return phaseCount to executor.commands
    }

    @Test
    fun `default config is 3 rounds of 30 breaths, then an exhale hold and a recovery hold`() {
        val (phaseCount, commands) = run(WimHofConfig())

        // Per round: 30 breaths * (inhale + exhale) + exhale hold + recovery hold.
        assertEquals(3 * (30 * 2 + 2), phaseCount)
        assertEquals(3, commands.count { it == ShowText(WimHofText.EXHALE_HOLD) })
        assertEquals(3, commands.count { it == ShowText(WimHofText.RECOVERY_HOLD) })
    }

    @Test
    fun `custom rounds and breaths are respected`() {
        val (phaseCount, _) = run(WimHofConfig(rounds = 2, breathsPerRound = 10))

        assertEquals(2 * (10 * 2 + 2), phaseCount)
    }

    @Test
    fun `the exhale hold is user-releasable so the practitioner can breathe whenever needed`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(wimHofMeditationDefinition(WimHofConfig(rounds = 1, breathsPerRound = 1)), executor)
        engine.send(MeditationEvent.Start)
        engine.send(MeditationEvent.Next) // inhale -> exhale
        engine.send(MeditationEvent.Next) // exhale -> exhale hold
        assertEquals(WimHofPhaseIds.EXHALE_HOLD, engine.state.value.currentPhaseId)

        engine.send(MeditationEvent.UserAction())

        assertEquals(WimHofPhaseIds.RECOVERY_HOLD, engine.state.value.currentPhaseId)
        assertTrue(executor.commands.contains(ShowText(WimHofText.RECOVERY_HOLD)))
    }

    private fun engineAtExhaleHold(config: WimHofConfig): MeditationEngine {
        val engine = MeditationEngine(wimHofMeditationDefinition(config), RecordingCommandExecutor())
        engine.send(MeditationEvent.Start)
        while (engine.state.value.currentPhaseId != WimHofPhaseIds.EXHALE_HOLD) {
            engine.send(MeditationEvent.Next)
        }
        return engine
    }

    @Test
    fun `the exhale hold is a fixed-length phase and its timeout advances to the recovery hold`() {
        val config = WimHofConfig(rounds = 1, breathsPerRound = 1)
        val round = (wimHofMeditationDefinition(config).root as Repeat).child as MeditationSequence
        val hold = round.children.filterIsInstance<Phase>().first { it.id == WimHofPhaseIds.EXHALE_HOLD }
        assertEquals(PhaseDuration.Fixed(config.exhaleHoldMillis), hold.duration)

        val engine = engineAtExhaleHold(config)
        engine.send(MeditationEvent.TimerCompleted(engine.state.value.timerGeneration))

        assertEquals(WimHofPhaseIds.RECOVERY_HOLD, engine.state.value.currentPhaseId)
    }

    @Test
    fun `UserAction is ignored during the recovery hold`() {
        val engine = engineAtExhaleHold(WimHofConfig(rounds = 1, breathsPerRound = 1))
        engine.send(MeditationEvent.UserAction())
        assertEquals(WimHofPhaseIds.RECOVERY_HOLD, engine.state.value.currentPhaseId)

        engine.send(MeditationEvent.UserAction())

        assertEquals(WimHofPhaseIds.RECOVERY_HOLD, engine.state.value.currentPhaseId)
        assertEquals(SessionStatus.Running, engine.state.value.status)
    }

    @Test
    fun `every round gets a fresh releasable exhale hold`() {
        val engine = engineAtExhaleHold(WimHofConfig(rounds = 2, breathsPerRound = 1))
        engine.send(MeditationEvent.UserAction()) // release round 1's hold
        engine.send(MeditationEvent.Next) // end recovery hold -> round 2
        assertEquals(1, engine.state.value.iterationCounts["rounds"])

        engine.send(MeditationEvent.Next) // inhale -> exhale
        engine.send(MeditationEvent.Next) // exhale -> exhale hold
        assertEquals(WimHofPhaseIds.EXHALE_HOLD, engine.state.value.currentPhaseId)
        engine.send(MeditationEvent.UserAction())

        assertEquals(WimHofPhaseIds.RECOVERY_HOLD, engine.state.value.currentPhaseId)
    }

    @Test
    fun `rounds and breathsPerRound are exposed as variables`() {
        val definition = wimHofMeditationDefinition(WimHofConfig(rounds = 2, breathsPerRound = 20))

        assertEquals(mapOf("rounds" to 2, "breathsPerRound" to 20), definition.variables)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive rounds are rejected`() {
        wimHofMeditationDefinition(WimHofConfig(rounds = 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-positive breaths are rejected`() {
        wimHofMeditationDefinition(WimHofConfig(breathsPerRound = 0))
    }
}
