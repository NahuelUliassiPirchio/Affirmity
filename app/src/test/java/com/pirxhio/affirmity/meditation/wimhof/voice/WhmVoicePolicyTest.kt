package com.pirxhio.affirmity.meditation.wimhof.voice

import com.pirxhio.affirmity.data.catalog.CatalogLocale
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
    fun `English gets the voice guide on by default and the stored toggle can turn it off`() {
        assertEquals("true", resolveVoiceGuide(emptyMap(), "en")["voiceGuide"])
        assertEquals("true", resolveVoiceGuide(emptyMap(), "en-US")["voiceGuide"])
        assertEquals("false", resolveVoiceGuide(mapOf("voiceGuide" to "false"), "en")["voiceGuide"])
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

    @Test
    fun `RESP and RET clip counts cover exactly the numbered clips in the generated table`() {
        listOf(CatalogLocale.ES to WhmClips.voiceMillis, CatalogLocale.EN to WhmClipsEn.voiceMillis).forEach { (locale, table) ->
            val resp = WhmVoicePolicy.respClipCount(locale)
            val ret = WhmVoicePolicy.retClipCount(locale)
            assertEquals(12, resp)
            assertEquals(11, ret)
            (1..resp).forEach { assertTrue(WhmVoicePolicy.respClipId(it, locale) in table) }
            (1..ret).forEach { assertTrue(WhmVoicePolicy.retClipId(it, locale) in table) }
            assertFalse(WhmVoicePolicy.respClipId(resp + 1, locale) in table)
            assertFalse(WhmVoicePolicy.retClipId(ret + 1, locale) in table)
        }
    }

    // --- Locale-aware clip ids ----------------------------------------------------------------

    @Test
    fun `the resolved customization carries the locale whose clips play`() {
        assertEquals("es", resolveVoiceGuide(emptyMap(), "es-AR")[VOICE_LOCALE_KEY])
        assertEquals("en", resolveVoiceGuide(emptyMap(), "en-GB")[VOICE_LOCALE_KEY])
        assertEquals(null, resolveVoiceGuide(emptyMap(), "fr")[VOICE_LOCALE_KEY])
        assertEquals(null, resolveVoiceGuide(emptyMap(), null)[VOICE_LOCALE_KEY])
    }

    @Test
    fun `every English clip id the policy can name exists in the English table and never in the Spanish one`() {
        val en = CatalogLocale.EN
        val ids = buildList {
            addAll(WhmVoicePolicy.introClips(en))
            addAll(WhmVoicePolicy.closingClips(en))
            add(WhmVoicePolicy.retInicio(en))
            add(WhmVoicePolicy.recInicio(en))
            add(WhmVoicePolicy.recCuenta(en))
            addAll((1..12).map { WhmVoicePolicy.respClipId(it, en) })
            addAll((1..11).map { WhmVoicePolicy.retClipId(it, en) })
            addAll((1..3).map { WhmVoicePolicy.entreClipId(it, en) })
            addAll((0..3).map { WhmVoicePolicy.rondaClipId(it, 4, en) })
            addAll(listOf(25, 29).mapNotNull { WhmVoicePolicy.countdownClip(it, 30, en) })
        }
        assertTrue(ids.all { it in WhmClipsEn.voiceMillis })
        assertTrue(ids.none { it in WhmClips.voiceMillis })
    }

    @Test
    fun `the default locale keeps the Spanish ids`() {
        assertEquals("whm_vo_resp_03", WhmVoicePolicy.respClipId(3))
        assertEquals("whm_vo_ronda_final", WhmVoicePolicy.rondaClipId(2, 3))
        assertEquals("whm_vo_cuenta_5", WhmVoicePolicy.countdownClip(25, 30))
        assertEquals(WhmVoicePolicy.INTRO_CLIPS, WhmVoicePolicy.introClips(CatalogLocale.ES))
        assertEquals(WhmVoicePolicy.CLOSING_CLIPS, WhmVoicePolicy.closingClips(CatalogLocale.ES))
    }

    // --- Per-locale timings --------------------------------------------------------------------

    @Test
    fun `Spanish timings are exactly the ones the Spanish guide always had`() {
        val es = CatalogLocale.ES
        assertEquals(25_500L, WhmVoicePolicy.introMillis(es))
        assertEquals(8_800L, WhmVoicePolicy.roundIntroMillis(es))
        assertEquals(16_400L, WhmVoicePolicy.recoveryVoiceMillis(es))
        assertEquals(20_000L, WhmVoicePolicy.closingMillis(es))
        assertEquals(12_720L, WhmVoicePolicy.recCuentaAtMillis(es))
        assertEquals(12_720L, WhmVoicePolicy.REC_CUENTA_AT_MILLIS)
        assertEquals(WhmVoicePolicy.closingMillis(), WhmVoicePolicy.closingMillis(es))
    }

    @Test
    fun `English intro and closing are sized from the English clips`() {
        val en = CatalogLocale.EN
        fun ms(id: String) = WhmClipsEn.voiceMillis.getValue(id)
        val intro = WhmVoicePolicy.introClips(en).sumOf(::ms) + 2 * WhmVoicePolicy.CHAIN_GAP_MILLIS + WhmVoicePolicy.CLOSING_TAIL_MILLIS
        val closing = WhmVoicePolicy.closingClips(en).sumOf(::ms) + 3 * WhmVoicePolicy.CHAIN_GAP_MILLIS + WhmVoicePolicy.CLOSING_TAIL_MILLIS

        assertEquals(24_500L, WhmVoicePolicy.introMillis(en))
        assertEquals(18_500L, WhmVoicePolicy.closingMillis(en))
        assertTrue(WhmVoicePolicy.introMillis(en) in intro..intro + 499)
        assertTrue(WhmVoicePolicy.closingMillis(en) in closing..closing + 499)
    }

    @Test
    fun `English recovery is REC_INICIO plus the silence plus REC_CUENTA plus a pad`() {
        val en = CatalogLocale.EN
        val inicio = WhmClipsEn.voiceMillis.getValue("whm_vo_en_rec_inicio")
        val cuenta = WhmClipsEn.voiceMillis.getValue("whm_vo_en_rec_cuenta")

        assertEquals(inicio + 10_165L, WhmVoicePolicy.recCuentaAtMillis(en))
        assertEquals(16_400L, WhmVoicePolicy.recoveryVoiceMillis(en))
        assertTrue(WhmVoicePolicy.recCuentaAtMillis(en) + cuenta <= WhmVoicePolicy.recoveryVoiceMillis(en))
    }

    @Test
    fun `English RONDA clips end inside the round intro after the longest ENTRE`() {
        val en = CatalogLocale.EN
        val roundIntro = WhmVoicePolicy.roundIntroMillis(en)
        val longestEntre = (1..3).maxOf { WhmClipsEn.voiceMillis.getValue(WhmVoicePolicy.entreClipId(it, en)) }
        (0..3).map { WhmVoicePolicy.rondaClipId(it, 4, en) }.forEach { ronda ->
            val start = WhmVoicePolicy.rondaStartMillis(ronda, en)
            assertEquals(roundIntro - WhmVoicePolicy.RONDA_TAIL_MILLIS - WhmClipsEn.voiceMillis.getValue(ronda), start)
            assertTrue(WhmVoicePolicy.ENTRE_AT_MILLIS + longestEntre <= start)
        }
    }

    @Test
    fun `English TIEMPO_400 still finishes before the 250 s hold ceiling`() {
        val mark = WhmVoiceClips.of(CatalogLocale.EN).tiempoMarks.last()
        assertEquals("whm_vo_en_tiempo_400", mark.second)
        assertTrue(mark.first + WhmClipsEn.voiceMillis.getValue(mark.second) < 250_000L)
    }
}
