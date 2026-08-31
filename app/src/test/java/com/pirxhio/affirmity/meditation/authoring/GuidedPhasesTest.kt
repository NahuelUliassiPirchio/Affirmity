package com.pirxhio.affirmity.meditation.authoring

import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.ShowLiteralText
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.StartAmbient
import com.pirxhio.affirmity.meditation.StopAmbient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidedPhasesTest {

    // --- cuedPhase ----------------------------------------------------------------------------

    @Test
    fun `cuedPhase with no cue has no onEnter commands`() {
        val phase = cuedPhase(id = "guide", duration = PhaseDuration.Fixed(1_000L))

        assertEquals("guide", phase.id)
        assertTrue(phase.onEnter.isEmpty())
    }

    @Test
    fun `cuedPhase shows only the text cue when no voice is given`() {
        val phase = cuedPhase(id = "guide", duration = PhaseDuration.Fixed(1_000L), cueTextId = "text")

        assertEquals(listOf(ShowText("text")), phase.onEnter)
    }

    @Test
    fun `cuedPhase shows text and plays voice when both are given`() {
        val phase = cuedPhase(
            id = "guide",
            duration = PhaseDuration.Fixed(1_000L),
            cueTextId = "text",
            cueVoiceId = "voice",
        )

        assertEquals(listOf(ShowText("text"), PlayVoice("voice")), phase.onEnter)
    }

    @Test
    fun `skippable cuedPhase exits on a user action`() {
        val phase = cuedPhase(id = "guide", duration = PhaseDuration.Fixed(1_000L), skippable = true)

        assertEquals(setOf(MeditationEvent.UserAction::class), phase.exitEvents)
    }

    @Test
    fun `a non-skippable cuedPhase has no exit events`() {
        val phase = cuedPhase(id = "guide", duration = PhaseDuration.Fixed(1_000L))

        assertTrue(phase.exitEvents.isEmpty())
    }

    // --- literalCuedPhase -----------------------------------------------------------------------

    @Test
    fun `literalCuedPhase shows the runtime string via ShowLiteralText`() {
        val phase = literalCuedPhase(id = "affirmation-1", duration = PhaseDuration.Fixed(5_000L), literalText = "I am capable.")

        assertEquals("affirmation-1", phase.id)
        assertEquals(listOf(ShowLiteralText("I am capable.")), phase.onEnter)
    }

    // --- chantPhase -----------------------------------------------------------------------------

    @Test
    fun `chantPhase plays the chant audio`() {
        val phase = chantPhase(id = "om", duration = PhaseDuration.Fixed(8_000L), audioId = "chant_om")

        assertEquals(listOf(PlayAudio("chant_om")), phase.onEnter)
    }

    @Test
    fun `chantPhase shows the text cue before playing audio when given`() {
        val phase = chantPhase(
            id = "om",
            duration = PhaseDuration.Fixed(8_000L),
            audioId = "chant_om",
            cueTextId = "text",
        )

        assertEquals(listOf(ShowText("text"), PlayAudio("chant_om")), phase.onEnter)
    }

    // --- rapidBreathCyclePhase --------------------------------------------------------------------

    @Test
    fun `rapidBreathCyclePhase starts the ambient bed on enter and stops it on exit`() {
        val phase = rapidBreathCyclePhase(
            id = "active-breathing",
            duration = PhaseDuration.Fixed(30_000L),
            ambientAudioId = "kapalabhati_bed",
        )

        assertEquals(listOf(StartAmbient("kapalabhati_bed", volume = 1f)), phase.onEnter)
        assertEquals(listOf(StopAmbient(0L)), phase.onExit)
    }

    @Test
    fun `rapidBreathCyclePhase shows the text cue before starting the bed when given`() {
        val phase = rapidBreathCyclePhase(
            id = "active-breathing",
            duration = PhaseDuration.Fixed(30_000L),
            ambientAudioId = "kapalabhati_bed",
            cueTextId = "text",
        )

        assertEquals(listOf(ShowText("text"), StartAmbient("kapalabhati_bed", volume = 1f)), phase.onEnter)
    }
}
