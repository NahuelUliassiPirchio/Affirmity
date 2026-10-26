package com.pirxhio.affirmity.meditation.wimhof

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.StopAudio
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
        assertEquals(250_000L, config.exhaleHoldMillis)
        assertEquals(PhaseDuration.Fixed(250_000L), hold.duration)

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

    // --- breath speed, breath sound, definition-authored SFX ----------------------------------------

    private fun sfxIds(commands: List<MeditationCommand>): List<String> =
        commands.filterIsInstance<PlayAudio>().map { it.audioId }.filter { it.startsWith("whm_sfx_") }

    private fun breathPhases(config: WimHofConfig): List<Phase> {
        val round = (wimHofMeditationDefinition(config).root as Repeat).child as MeditationSequence
        val breathing = round.children.first { it.id == "breathing" } as Repeat
        return (breathing.child as MeditationSequence).children.map { it as Phase }
    }

    @Test
    fun `breath speed options are slow 2000-2000, normal 1700-1700 and fast 1200-1000`() {
        assertEquals(listOf(2_000L to 2_000L), listOf(WimHofBreathSpeed.SLOW).map { it.inhaleMillis to it.exhaleMillis })
        assertEquals(listOf(1_700L to 1_700L), listOf(WimHofBreathSpeed.NORMAL).map { it.inhaleMillis to it.exhaleMillis })
        assertEquals(listOf(1_200L to 1_000L), listOf(WimHofBreathSpeed.FAST).map { it.inhaleMillis to it.exhaleMillis })
    }

    @Test
    fun `speed keys resolve and off-list or missing falls back to normal`() {
        assertEquals(WimHofBreathSpeed.SLOW, WimHofBreathSpeed.fromKey("slow"))
        assertEquals(WimHofBreathSpeed.FAST, WimHofBreathSpeed.fromKey("fast"))
        listOf(null, "", "turbo", "SLOW ", "1.5").forEach {
            assertEquals("key=$it", WimHofBreathSpeed.NORMAL, WimHofBreathSpeed.fromKey(it))
        }
    }

    @Test
    fun `default config is normal speed with breath sound on`() {
        val config = WimHofConfig()

        assertEquals(WimHofBreathSpeed.NORMAL, config.breathSpeed)
        assertEquals(true, config.breathSound)
        assertEquals(1_700L, config.inhaleMillis)
        assertEquals(1_700L, config.exhaleMillis)
    }

    @Test
    fun `the breathing phases use the selected speed durations`() {
        WimHofBreathSpeed.entries.forEach { speed ->
            val (inhale, exhale) = breathPhases(WimHofConfig(breathSpeed = speed))

            assertEquals(PhaseDuration.Fixed(speed.inhaleMillis), inhale.duration)
            assertEquals(PhaseDuration.Fixed(speed.exhaleMillis), exhale.duration)
        }
    }

    @Test
    fun `each cycle plays the inhale then exhale sfx matching the speed`() {
        WimHofBreathSpeed.entries.forEach { speed ->
            val (_, commands) = run(WimHofConfig(rounds = 1, breathsPerRound = 3, breathSpeed = speed))

            val expectedCycle = listOf("whm_sfx_inhale_${speed.key}", "whm_sfx_exhale_${speed.key}")
            assertEquals(
                expectedCycle + expectedCycle + expectedCycle + listOf("whm_sfx_exhale_hold", "whm_sfx_inhale_recovery"),
                sfxIds(commands),
            )
        }
    }

    @Test
    fun `the exhale hold starts with the total-exhale sfx and the recovery with the recovery-inhale sfx`() {
        val round = (wimHofMeditationDefinition(WimHofConfig()).root as Repeat).child as MeditationSequence
        val hold = round.children.first { it.id == WimHofPhaseIds.EXHALE_HOLD } as Phase
        val recovery = round.children.first { it.id == WimHofPhaseIds.RECOVERY_HOLD } as Phase

        assertTrue(hold.onEnter.contains(PlayAudio("whm_sfx_exhale_hold")))
        assertTrue(recovery.onEnter.contains(PlayAudio("whm_sfx_inhale_recovery")))
    }

    @Test
    fun `breathSound off authors no whm sfx playback at all`() {
        val (_, commands) = run(WimHofConfig(rounds = 2, breathsPerRound = 3, breathSound = false))

        assertEquals(emptyList<String>(), sfxIds(commands))
        assertTrue(commands.none { it is PlayAudio })
    }

    @Test
    fun `breath sfx are independent of the cue-sound toggle because they are PlayAudio not PlayCue`() {
        val (_, commands) = run(WimHofConfig(rounds = 1, breathsPerRound = 1))

        assertTrue(sfxIds(commands).isNotEmpty())
        assertTrue(commands.filterIsInstance<PlayAudio>().all { it.audioId.startsWith("whm_sfx_") })
    }

    @Test
    fun `the exhale hold estimates a typical 90 s while keeping the 250 s ceiling`() {
        val round = (wimHofMeditationDefinition(WimHofConfig()).root as Repeat).child as MeditationSequence
        val hold = round.children.first { it.id == WimHofPhaseIds.EXHALE_HOLD } as Phase

        assertEquals(90_000L, hold.estimatedMillis)
        assertEquals(PhaseDuration.Fixed(250_000L), hold.duration)
        assertEquals(null, (round.children.first { it.id == WimHofPhaseIds.RECOVERY_HOLD } as Phase).estimatedMillis)
    }

    // --- Skip leaves the whole breathing block --------------------------------------------------

    @Test
    fun `the breathing repeat is marked so Skip exits the whole block`() {
        val round = (wimHofMeditationDefinition(WimHofConfig()).root as Repeat).child as MeditationSequence
        val breathing = round.children.first { it.id == "breathing" } as Repeat

        assertTrue(breathing.skipExitsBlock)
    }

    @Test
    fun `Skip at breath 7 of 30 lands on the exhale hold and no further inhale sfx plays`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(wimHofMeditationDefinition(WimHofConfig(rounds = 1, breathsPerRound = 30)), executor)
        engine.send(MeditationEvent.Start)
        repeat(6 * 2) { engine.send(MeditationEvent.Next) }
        assertEquals(6, engine.state.value.iterationCounts["breathing"])
        val inhalesBefore = executor.commands.count { it == PlayAudio("whm_sfx_inhale_normal") }

        engine.send(MeditationEvent.Skip)

        assertEquals(WimHofPhaseIds.EXHALE_HOLD, engine.state.value.currentPhaseId)
        assertEquals(inhalesBefore, executor.commands.count { it == PlayAudio("whm_sfx_inhale_normal") })
    }

    @Test
    fun `Skip on the very last breath of the block lands on the exhale hold without advancing twice`() {
        listOf(0, 1).forEach { phasesIntoLastBreath ->
            val executor = RecordingCommandExecutor()
            val engine = MeditationEngine(wimHofMeditationDefinition(WimHofConfig(rounds = 2, breathsPerRound = 30)), executor)
            engine.send(MeditationEvent.Start)
            repeat(29 * 2 + phasesIntoLastBreath) { engine.send(MeditationEvent.Next) }
            assertEquals(29, engine.state.value.iterationCounts["breathing"])
            val inhalesBefore = executor.commands.count { it == PlayAudio("whm_sfx_inhale_normal") }

            engine.send(MeditationEvent.Skip)

            assertEquals("phase $phasesIntoLastBreath", WimHofPhaseIds.EXHALE_HOLD, engine.state.value.currentPhaseId)
            assertEquals(null, engine.state.value.iterationCounts["breathing"])
            assertEquals(0, engine.state.value.iterationCounts["rounds"])
            assertEquals(inhalesBefore, executor.commands.count { it == PlayAudio("whm_sfx_inhale_normal") })

            // The hold is a single step: the next advance goes to recovery, not past it.
            engine.send(MeditationEvent.Next)
            assertEquals(WimHofPhaseIds.RECOVERY_HOLD, engine.state.value.currentPhaseId)
        }
    }

    @Test
    fun `the exhale hold first stops any in-flight breath sfx, then plays the total-exhale sfx`() {
        val round = (wimHofMeditationDefinition(WimHofConfig()).root as Repeat).child as MeditationSequence
        val hold = round.children.first { it.id == WimHofPhaseIds.EXHALE_HOLD } as Phase

        val audio = hold.onEnter.filter { it is StopAudio || it is PlayAudio }
        assertEquals(
            listOf(
                StopAudio("whm_sfx_inhale_normal"),
                StopAudio("whm_sfx_exhale_normal"),
                PlayAudio("whm_sfx_exhale_hold"),
            ),
            audio,
        )
    }

    @Test
    fun `breathSound off authors no sfx stop either`() {
        val round = (wimHofMeditationDefinition(WimHofConfig(breathSound = false)).root as Repeat).child as MeditationSequence
        val hold = round.children.first { it.id == WimHofPhaseIds.EXHALE_HOLD } as Phase

        assertTrue(hold.onEnter.none { it is StopAudio || it is PlayAudio })
    }
}
