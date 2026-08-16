package com.pirxhio.affirmity.meditation.authoring

import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural assertions only — never whole-tree `assertEquals` — per the binding
 * `FixedCountRepetition`/`FadeCurve` identity-equality caveat (design §7.3): a hand-built
 * comparison tree would fail for reasons unrelated to the builder under test.
 */
class MeditationAuthoringTest {

    // --- restPhase --------------------------------------------------------------------------

    @Test
    fun `SILENCE rest has no onEnter commands`() {
        val phase = restPhase(id = "rest", kind = RestKind.SILENCE, duration = PhaseDuration.Fixed(1_000L))

        assertEquals("rest", phase.id)
        assertTrue(phase.onEnter.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SILENCE rest rejects a text cue`() {
        restPhase(id = "rest", kind = RestKind.SILENCE, duration = PhaseDuration.Fixed(1_000L), cueTextId = "text")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `SILENCE rest rejects a voice cue`() {
        restPhase(id = "rest", kind = RestKind.SILENCE, duration = PhaseDuration.Fixed(1_000L), cueVoiceId = "voice")
    }

    @Test
    fun `AMBIENT rest shows only the text cue and emits no audio command`() {
        val phase = restPhase(
            id = "rest",
            kind = RestKind.AMBIENT,
            duration = PhaseDuration.Fixed(1_000L),
            cueTextId = "text",
        )

        assertEquals(listOf(ShowText("text")), phase.onEnter)
    }

    @Test
    fun `BREATH_AWARENESS rest shows text and plays the voice cue`() {
        val phase = restPhase(
            id = "rest",
            kind = RestKind.BREATH_AWARENESS,
            duration = PhaseDuration.Fixed(1_000L),
            cueTextId = "text",
            cueVoiceId = "voice",
        )

        assertEquals(listOf(ShowText("text"), PlayVoice("voice")), phase.onEnter)
    }

    @Test
    fun `OPEN_AWARENESS rest has the identical shape to BREATH_AWARENESS`() {
        val phase = restPhase(
            id = "rest",
            kind = RestKind.OPEN_AWARENESS,
            duration = PhaseDuration.Fixed(1_000L),
            cueTextId = "text",
            cueVoiceId = "voice",
        )

        assertEquals(listOf(ShowText("text"), PlayVoice("voice")), phase.onEnter)
    }

    @Test
    fun `skippable rest exits on a user action`() {
        val phase = restPhase(
            id = "rest",
            kind = RestKind.SILENCE,
            duration = PhaseDuration.Fixed(1_000L),
            skippable = true,
        )

        assertEquals(setOf(MeditationEvent.UserAction::class), phase.exitEvents)
    }

    @Test
    fun `a non-skippable rest has no exit events`() {
        val phase = restPhase(id = "rest", kind = RestKind.SILENCE, duration = PhaseDuration.Fixed(1_000L))

        assertTrue(phase.exitEvents.isEmpty())
    }

    // --- breathingBlock -----------------------------------------------------------------------

    @Test
    fun `breathingBlock produces the default ids and node shape`() {
        val block = breathingBlock(breaths = 3, inhaleMillis = 1_000L, exhaleMillis = 1_000L)

        assertEquals("breathing", block.id)
        val breath = block.child as com.pirxhio.affirmity.meditation.MeditationSequence
        assertEquals("breath", breath.id)
        assertEquals(2, breath.children.size)
        assertEquals("inhale", breath.children[0].id)
        assertEquals("exhale", breath.children[1].id)
    }

    @Test
    fun `breathingBlock omits hold phases by default`() {
        val block = breathingBlock(breaths = 3, inhaleMillis = 1_000L, exhaleMillis = 1_000L)

        val breath = block.child as com.pirxhio.affirmity.meditation.MeditationSequence
        assertEquals(2, breath.children.size)
    }

    @Test
    fun `breathingBlock includes hold phases only when their millis are non-zero`() {
        val block = breathingBlock(
            breaths = 3,
            inhaleMillis = 1_000L,
            exhaleMillis = 1_000L,
            holdAfterInhaleMillis = 500L,
            holdAfterExhaleMillis = 250L,
        )

        val breath = block.child as com.pirxhio.affirmity.meditation.MeditationSequence
        assertEquals(4, breath.children.size)
        assertEquals(listOf("inhale", "hold_in", "exhale", "hold_out"), breath.children.map { it.id })
    }

    @Test
    fun `breathingBlock respects custom ids`() {
        val block = breathingBlock(
            id = "custom-breathing",
            breathId = "custom-breath",
            inhaleId = "custom-inhale",
            exhaleId = "custom-exhale",
            breaths = 1,
            inhaleMillis = 1_000L,
            exhaleMillis = 1_000L,
        )

        assertEquals("custom-breathing", block.id)
        val breath = block.child as com.pirxhio.affirmity.meditation.MeditationSequence
        assertEquals("custom-breath", breath.id)
        assertEquals(listOf("custom-inhale", "custom-exhale"), breath.children.map { it.id })
    }

    // --- breathingMeditationDefinition equivalence after its breathingBlock() re-expression --

    @Test
    fun `the refactored breathing definition drives the engine through the identical activePath sequence`() {
        val config = com.pirxhio.affirmity.meditation.breathing.BreathingConfig(
            rounds = 2,
            breathsPerRound = 2,
            inhaleMillis = 1_000,
            exhaleMillis = 1_000,
            retentionMillis = 1_500,
            recoveryMillis = 500,
        )
        val executor = object : com.pirxhio.affirmity.meditation.MeditationCommandExecutor {
            override fun execute(command: com.pirxhio.affirmity.meditation.MeditationCommand) = Unit
        }
        val engine = com.pirxhio.affirmity.meditation.MeditationEngine(
            com.pirxhio.affirmity.meditation.breathing.breathingMeditationDefinition(config),
            executor,
        )
        val recordedPaths = mutableListOf<List<String>>()
        fun record() = recordedPaths.add(engine.state.value.activePath)

        engine.send(MeditationEvent.Start)
        record()
        assertEquals(listOf("rounds", "round", "breathing", "breath", "inhale"), recordedPaths.last())

        repeat(4) {
            engine.send(MeditationEvent.Next)
            record()
        }
        assertEquals("retention", recordedPaths.last().last())

        engine.send(MeditationEvent.Next)
        record()
        assertEquals("recovery", recordedPaths.last().last())

        engine.send(MeditationEvent.Next)
        record()
        assertEquals(listOf("rounds", "round", "breathing", "breath", "inhale"), recordedPaths.last())
        assertEquals(1, engine.state.value.iterationCounts["rounds"])
    }

    @Test
    fun `the refactored breathing definition preserves every existing id verbatim`() {
        val definition = com.pirxhio.affirmity.meditation.breathing.breathingMeditationDefinition(
            com.pirxhio.affirmity.meditation.breathing.BreathingConfig(),
        )
        val ids = collectIds(definition.root)

        assertTrue(ids.contains("breathing"))
        assertTrue(ids.contains("breath"))
        assertTrue(ids.contains("inhale"))
        assertTrue(ids.contains("exhale"))
    }

    private fun collectIds(node: com.pirxhio.affirmity.meditation.MeditationNode): Set<String> =
        when (node) {
            is com.pirxhio.affirmity.meditation.Phase -> setOf(node.id)
            is com.pirxhio.affirmity.meditation.MeditationSequence ->
                setOf(node.id) + node.children.flatMap { collectIds(it) }
            is com.pirxhio.affirmity.meditation.Repeat ->
                setOf(node.id) + collectIds(node.child)
        }
}
