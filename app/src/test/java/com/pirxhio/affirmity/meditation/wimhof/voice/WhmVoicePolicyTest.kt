package com.pirxhio.affirmity.meditation.wimhof.voice

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhmVoicePolicyTest {

    // --- resolveVoiceGuide matrix --------------------------------------------------------------

    @Test
    fun `Spanish with no stored value turns the voice guide on`() {
        assertEquals("true", resolveVoiceGuide(emptyMap(), "es")["voiceGuide"])
    }

    @Test
    fun `Spanish honours the stored toggle`() {
        assertEquals("false", resolveVoiceGuide(mapOf("voiceGuide" to "false"), "es")["voiceGuide"])
        assertEquals("true", resolveVoiceGuide(mapOf("voiceGuide" to "true"), "es")["voiceGuide"])
    }

    @Test
    fun `a region-qualified Spanish tag counts as Spanish`() {
        assertEquals("true", resolveVoiceGuide(emptyMap(), "es-AR")["voiceGuide"])
        assertEquals("true", resolveVoiceGuide(emptyMap(), "ES")["voiceGuide"])
    }

    @Test
    fun `English never gets the voice guide even if a stored value says true`() {
        assertEquals("false", resolveVoiceGuide(mapOf("voiceGuide" to "true"), "en")["voiceGuide"])
        assertEquals("false", resolveVoiceGuide(emptyMap(), "en-US")["voiceGuide"])
    }

    @Test
    fun `a missing or unknown locale gets no voice guide`() {
        assertEquals("false", resolveVoiceGuide(emptyMap(), null)["voiceGuide"])
        assertEquals("false", resolveVoiceGuide(emptyMap(), "")["voiceGuide"])
        assertEquals("false", resolveVoiceGuide(emptyMap(), "fr")["voiceGuide"])
    }

    @Test
    fun `an unparseable stored value falls back to on for Spanish and other keys are kept`() {
        val resolved = resolveVoiceGuide(mapOf("voiceGuide" to "maybe", "rounds" to "2"), "es")

        assertEquals("true", resolved["voiceGuide"])
        assertEquals("2", resolved["rounds"])
    }

    // --- countdown ------------------------------------------------------------------------------

    @Test
    fun `countdown clips are chosen by breaths remaining`() {
        listOf(20, 30, 40).forEach { n ->
            assertEquals("whm_vo_cuenta_5", WhmVoicePolicy.countdownClip(n - 5, n))
            assertEquals("whm_vo_cuenta_ultima", WhmVoicePolicy.countdownClip(n - 1, n))
            assertNull(WhmVoicePolicy.countdownClip(n - 4, n))
            assertNull(WhmVoicePolicy.countdownClip(0, n))
        }
    }

    @Test
    fun `RESP is allowed only before the last eight breaths`() {
        assertTrue(WhmVoicePolicy.respAllowed(21, 30))
        assertFalse(WhmVoicePolicy.respAllowed(22, 30))
    }

    // --- optional clip fit ------------------------------------------------------------------------

    @Test
    fun `an optional clip fits when it ends at least 300 ms before the next required mark`() {
        assertTrue(WhmVoicePolicy.fitsBefore(startMillis = 27_000, clipMillis = 2_700, nextRequiredMillis = 30_000))
        assertFalse(WhmVoicePolicy.fitsBefore(startMillis = 27_000, clipMillis = 2_701, nextRequiredMillis = 30_000))
        assertTrue(WhmVoicePolicy.fitsBefore(startMillis = 27_000, clipMillis = 60_000, nextRequiredMillis = null))
    }

    // --- random draws -------------------------------------------------------------------------------

    @Test
    fun `gap draws stay inside their ranges`() {
        val random = Random(1)
        repeat(500) {
            assertTrue(WhmVoicePolicy.respGapCycles(random) in 4..6)
            assertTrue(WhmVoicePolicy.retGapMillis(random) in 20_000..30_000)
        }
    }
}
