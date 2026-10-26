package com.pirxhio.affirmity.meditation.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log10

class SfxDuckPolicyTest {

    @Test
    fun `sfx play at full gain when no voice is playing`() {
        assertEquals(1f, SfxDuckPolicy.gain(voicePlaying = false), 0f)
    }

    @Test
    fun `sfx are ducked to 0_42 while a voice plays`() {
        assertEquals(0.42f, SfxDuckPolicy.gain(voicePlaying = true), 0f)
    }

    @Test
    fun `the duck is between 6 and 8 dB`() {
        val db = -20 * log10(SfxDuckPolicy.gain(voicePlaying = true).toDouble())

        assertTrue("duck was $db dB", db in 6.0..8.0)
    }
}
