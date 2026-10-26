package com.pirxhio.affirmity.meditation.wimhof.voice

import com.pirxhio.affirmity.data.catalog.CatalogLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhmVoiceClipsTest {

    private val es = WhmVoiceClips.of(CatalogLocale.ES)
    private val en = WhmVoiceClips.of(CatalogLocale.EN)

    private fun stems(clips: WhmVoiceClips): Set<String> =
        clips.voiceMillis.keys.map { it.removePrefix(clips.id("")) }.toSet()

    @Test
    fun `the Spanish set is the existing generated table, untouched`() {
        assertEquals(WhmClips.voiceMillis, es.voiceMillis)
        assertEquals("whm_vo_resp_01", es.id("resp_01"))
    }

    @Test
    fun `the English set has the same 52 clip stems under its own whm_vo_en_ names`() {
        assertEquals(52, en.voiceMillis.size)
        assertEquals(stems(es), stems(en))
        assertEquals("whm_vo_en_resp_01", en.id("resp_01"))
        assertTrue(en.voiceMillis.keys.all { it.startsWith("whm_vo_en_") })
        assertTrue(es.voiceMillis.keys.none { it.startsWith("whm_vo_en_") })
    }

    @Test
    fun `both locales ship clips so both can play the guide`() {
        assertTrue(es.isAvailable)
        assertTrue(en.isAvailable)
        assertEquals(setOf("es", "en"), VOICE_GUIDE_LOCALES)
    }

    @Test
    fun `the English RET_11 is the 4 second re-recording, not the 3_16 second zip clip`() {
        val millis = requireNotNull(en.durationMillis(en.id("ret_11")))
        assertTrue("$millis", millis in 3_900L..4_100L)
    }

    @Test
    fun `numbered clip counts and the time marks follow each locale's own table`() {
        assertEquals(12, en.respCount)
        assertEquals(11, en.retCount)
        assertEquals(WhmClips.tiempoBySeconds.keys.sorted().map { it * 1_000L }, en.tiempoMarks.map { it.first })
        assertTrue(en.tiempoMarks.all { it.second.startsWith("whm_vo_en_tiempo_") })
        assertTrue(es.tiempoMarks.all { it.second.startsWith("whm_vo_tiempo_") })
    }

    @Test
    fun `durations come from the locale's own clips and the shared SFX`() {
        assertEquals(WhmClipsEn.voiceMillis["whm_vo_en_intro_1"], en.durationMillis("whm_vo_en_intro_1"))
        assertEquals(WhmClips.voiceMillis["whm_vo_intro_1"], es.durationMillis("whm_vo_intro_1"))
        assertFalse(en.durationMillis("whm_vo_intro_1") != null)
        assertTrue(en.durationMillis("whm_sfx_bowl") != null)
    }
}
