package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.meditation.EndLap
import com.pirxhio.affirmity.meditation.FixedCountRepetition
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.StartLap
import com.pirxhio.affirmity.meditation.breathing.BreathingAudio
import com.pirxhio.affirmity.meditation.breathing.BreathingConfig
import com.pirxhio.affirmity.meditation.breathing.BreathingText
import com.pirxhio.affirmity.meditation.breathing.breathingMeditationDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [collectPhases]/[collectCommands]/[fixedPhaseDurationsById] (REQ-4.8, design §5.3).
 * [fixedPhaseDurationsById] MUST be a byte-for-byte-equivalent replacement for the deleted
 * `phaseTotalMillis(phaseId, config)` `when` when applied to [breathingMeditationDefinition]'s
 * tree — this is the regression proof for that claim.
 */
class MeditationTreeDerivationTest {

    // --- fixedPhaseDurationsById: exact map over a non-default BreathingConfig -----------------

    @Test
    fun `fixedPhaseDurationsById returns the exact per-phase-id duration map for a non-default BreathingConfig`() {
        val definition = breathingMeditationDefinition(
            BreathingConfig(
                inhaleMillis = 1_111,
                exhaleMillis = 2_222,
                retentionMillis = 3_333,
                recoveryMillis = 4_444,
            ),
        )

        val durations = fixedPhaseDurationsById(definition)

        assertEquals(
            mapOf(
                "inhale" to 1_111L,
                "exhale" to 2_222L,
                "retention" to 3_333L,
                "recovery" to 4_444L,
            ),
            durations,
        )
    }

    // --- Manual-release phases are absent from the fixed-duration map --------------------------

    @Test
    fun `fixedPhaseDurationsById excludes Manual-duration phases from the map`() {
        val definition = MeditationDefinition(
            id = "manual-fixture",
            root = MeditationSequence(
                id = "root",
                children = listOf(
                    Phase(id = "fixed_phase", duration = PhaseDuration.Fixed(5_000)),
                    Phase(id = "manual_phase", duration = PhaseDuration.Manual),
                ),
            ),
        )

        val durations = fixedPhaseDurationsById(definition)

        assertEquals(mapOf("fixed_phase" to 5_000L), durations)
        assertFalse(durations.containsKey("manual_phase"))
    }

    // --- collectPhases walks into Repeat children, not just flat top-level lists ----------------

    @Test
    fun `collectPhases walks into a Repeat node's child, including a Repeat nested inside another Repeat`() {
        val definition = breathingMeditationDefinition(BreathingConfig())

        val phaseIds = collectPhases(definition.root).map { it.id }

        // root = Repeat("rounds") -> MeditationSequence("round") ->
        //   [Repeat("breathing") -> MeditationSequence("breath") -> [Phase("inhale"), Phase("exhale")],
        //    Phase("retention"), Phase("recovery")]
        assertEquals(listOf("inhale", "exhale", "retention", "recovery"), phaseIds)
    }

    @Test
    fun `collectPhases on a hand-built nested Repeat-of-Repeat tree returns every leaf phase in order`() {
        val innerRepeat = Repeat(
            id = "inner",
            child = MeditationSequence(
                id = "inner_seq",
                children = listOf(
                    Phase(id = "a", duration = PhaseDuration.Fixed(1_000)),
                    Phase(id = "b", duration = PhaseDuration.Fixed(1_000)),
                ),
            ),
            strategy = FixedCountRepetition(2),
        )
        val outerRepeat = Repeat(
            id = "outer",
            child = MeditationSequence(
                id = "outer_seq",
                children = listOf(
                    innerRepeat,
                    Phase(id = "c", duration = PhaseDuration.Fixed(1_000)),
                ),
            ),
            strategy = FixedCountRepetition(3),
        )

        val phaseIds = collectPhases(outerRepeat).map { it.id }

        assertEquals(listOf("a", "b", "c"), phaseIds)
    }

    // --- collectCommands flattens onEnter + onExit across every collected phase -----------------

    @Test
    fun `collectCommands flattens onEnter and onExit commands across all phases, including inside a Repeat`() {
        val definition = breathingMeditationDefinition(BreathingConfig())

        val commands = collectCommands(definition.root)

        assertTrue(commands.contains(ShowText(BreathingText.INHALE)))
        assertTrue(commands.contains(ShowText(BreathingText.EXHALE)))
        assertTrue(commands.contains(ShowText(BreathingText.RETENTION)))
        assertTrue(commands.contains(PlayAudio(BreathingAudio.RETENTION_START)))
        assertTrue(commands.contains(StartLap("retention")))
        assertTrue(commands.contains(EndLap("retention")))
        assertTrue(commands.contains(ShowText(BreathingText.RECOVERY)))
        // inhale onEnter(1) + exhale onEnter(1) + retention onEnter(3) + onExit(1) + recovery onEnter(1) = 7
        assertEquals(7, commands.size)
    }
}
