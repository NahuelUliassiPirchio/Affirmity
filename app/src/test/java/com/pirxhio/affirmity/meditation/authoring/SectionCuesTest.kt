package com.pirxhio.affirmity.meditation.authoring

import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationNode
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PlayCue
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.audio.MeditationCue
import com.pirxhio.affirmity.meditation.bodyscan.bodyScanMeditationDefinition
import com.pirxhio.affirmity.meditation.metta.mettaMeditationDefinition
import com.pirxhio.affirmity.meditation.progressivemusclerelaxation.ProgressiveMuscleRelaxationConfig
import com.pirxhio.affirmity.meditation.progressivemusclerelaxation.progressiveMuscleRelaxationMeditationDefinition
import com.pirxhio.affirmity.meditation.yoganidra.yogaNidraMeditationDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionCuesTest {

    private fun phases(node: MeditationNode): List<Phase> = when (node) {
        is Phase -> listOf(node)
        is MeditationSequence -> node.children.flatMap(::phases)
        is Repeat -> phases(node.child)
    }

    private fun cued(def: MeditationDefinition): Set<String> =
        phases(def.root).filter { p -> p.onEnter.any { it is PlayCue && it.cue == MeditationCue.SectionTransition } }
            .map { it.id }.toSet()

    @Test
    fun `withEntryCue prepends the cue and keeps the rest of the phase`() {
        val phase = Phase(id = "p", onEnter = listOf(com.pirxhio.affirmity.meditation.ShowText("t")))

        val result = phase.withEntryCue(MeditationCue.SectionTransition)

        assertEquals(listOf(PlayCue(MeditationCue.SectionTransition), com.pirxhio.affirmity.meditation.ShowText("t")), result.onEnter)
        assertEquals(phase.duration, result.duration)
    }

    @Test
    fun `body scan cues every region but not the first phase`() {
        val def = bodyScanMeditationDefinition()
        val cues = cued(def)

        assertEquals(setOf("bs_feet", "bs_legs", "bs_torso", "bs_arms", "bs_head", "bs_whole", "bs_close"), cues)
        assertTrue("bs_intro" !in cues)
    }

    @Test
    fun `yoga nidra cues sections after settling`() {
        val cues = cued(yogaNidraMeditationDefinition())

        assertTrue("settling" !in cues)
        assertEquals(
            setOf(
                "intention", "body_feet", "body_legs", "body_abdomen", "body_chest", "body_arms", "body_head",
                "breath_awareness", "visualization", "return",
            ),
            cues,
        )
    }

    @Test
    fun `metta cues every target after the first`() {
        assertEquals(
            setOf("loved_one", "neutral_person", "difficult_person", "all_beings"),
            cued(mettaMeditationDefinition()),
        )
    }

    @Test
    fun `pmr cues each new exercise and the final rest, never tense or relax sub-steps`() {
        val config = ProgressiveMuscleRelaxationConfig(muscleGroups = listOf("feet", "legs"))
        val cues = cued(progressiveMuscleRelaxationMeditationDefinition(config))

        assertEquals(setOf("tense_feet", "tense_legs", "whole_body_rest"), cues)
    }
}
