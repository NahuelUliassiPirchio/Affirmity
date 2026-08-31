package com.pirxhio.affirmity.meditation.authoring

import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatedCuePhasesTest {

    // --- bellPhase ------------------------------------------------------------------------------

    @Test
    fun `bellPhase repeats a single strike phase count times`() {
        val repeat = bellPhase(id = "opening-bell", count = 3, audioId = "bell")

        assertEquals("opening-bell", repeat.id)
        val strike = repeat.child as Phase
        assertEquals("strike", strike.id)
        assertEquals(listOf(PlayAudio("bell")), strike.onEnter)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `bellPhase rejects a non-positive count`() {
        bellPhase(id = "opening-bell", count = 0, audioId = "bell")
    }

    // --- countedRepetitionPhase -----------------------------------------------------------------

    @Test
    fun `countedRepetitionPhase repeats a cued phase count times`() {
        val repeat = countedRepetitionPhase(
            id = "repetition",
            count = 33,
            repetitionDurationMillis = 2_000L,
            cueTextId = "text",
            cueVoiceId = "voice",
        )

        assertEquals("repetition", repeat.id)
        val phrase = repeat.child as Phase
        assertEquals(PhaseDuration.Fixed(2_000L), phrase.duration)
        assertEquals(listOf(ShowText("text"), PlayVoice("voice")), phrase.onEnter)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `countedRepetitionPhase rejects a non-positive count`() {
        countedRepetitionPhase(id = "repetition", count = 0, repetitionDurationMillis = 1_000L)
    }
}
