package com.pirxhio.affirmity.meditation.wimhof.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhmClipsTest {

    private val expectedVoiceStems: List<String> =
        listOf("INTRO_1", "INTRO_2", "INTRO_3") +
            listOf("RONDA_1", "RONDA_2", "RONDA_3", "RONDA_4", "RONDA_FINAL") +
            listOf("ENTRE_1", "ENTRE_2", "ENTRE_3") +
            (1..12).map { "RESP_%02d".format(it) } +
            listOf("CUENTA_3", "CUENTA_5", "CUENTA_ULTIMA") +
            listOf("RET_INICIO") + (1..11).map { "RET_%02d".format(it) } +
            listOf("TIEMPO_030", "TIEMPO_100", "TIEMPO_130", "TIEMPO_200", "TIEMPO_230", "TIEMPO_300", "TIEMPO_330", "TIEMPO_400") +
            listOf("REC_INICIO", "REC_CUENTA") +
            (1..4).map { "CIERRE_$it" }

    private val expectedSfxIds = listOf(
        "inhale_slow", "exhale_slow", "inhale_normal", "exhale_normal",
        "inhale_fast", "exhale_fast", "exhale_hold", "inhale_recovery", "bowl",
    ).map { "whm_sfx_$it" }

    @Test
    fun `all 52 voice clips are present with the whm_vo prefix and lowercase stem`() {
        val expected = expectedVoiceStems.map { "whm_vo_" + it.lowercase() }.toSet()

        assertEquals(52, expectedVoiceStems.size)
        assertEquals(expected, WhmClips.voiceMillis.keys)
    }

    @Test
    fun `all 9 sfx clips are present`() {
        assertEquals(expectedSfxIds.toSet(), WhmClips.sfxMillis.keys)
    }

    @Test
    fun `every clip has a positive duration`() {
        (WhmClips.voiceMillis + WhmClips.sfxMillis).forEach { (id, millis) ->
            assertTrue("$id duration must be > 0 but was $millis", millis > 0L)
        }
    }

    @Test
    fun `breath sfx fit their phase length after leading-silence trim`() {
        mapOf(
            "whm_sfx_inhale_slow" to 2_000L,
            "whm_sfx_exhale_slow" to 2_000L,
            "whm_sfx_inhale_normal" to 1_700L,
            "whm_sfx_exhale_normal" to 1_700L,
            "whm_sfx_inhale_fast" to 1_200L,
            "whm_sfx_exhale_fast" to 1_000L,
        ).forEach { (id, expected) ->
            val actual = requireNotNull(WhmClips.durationMillis(id)) { "$id missing" }
            assertTrue("$id ($actual ms) must fit a $expected ms phase (+100 ms slack)", actual <= expected + 100L)
            assertTrue("$id ($actual ms) should cover most of a $expected ms phase", actual >= expected * 6 / 10)
        }
    }

    @Test
    fun `tiempo table maps the 8 m_ss stems to hold seconds 30 to 240`() {
        assertEquals(
            mapOf(
                30 to "whm_vo_tiempo_030",
                60 to "whm_vo_tiempo_100",
                90 to "whm_vo_tiempo_130",
                120 to "whm_vo_tiempo_200",
                150 to "whm_vo_tiempo_230",
                180 to "whm_vo_tiempo_300",
                210 to "whm_vo_tiempo_330",
                240 to "whm_vo_tiempo_400",
            ),
            WhmClips.tiempoBySeconds,
        )
    }

    @Test
    fun `durationMillis resolves voice and sfx ids and null for unknown`() {
        assertTrue(requireNotNull(WhmClips.durationMillis("whm_vo_intro_1")) > 0L)
        assertTrue(requireNotNull(WhmClips.durationMillis("whm_sfx_bowl")) > 0L)
        assertEquals(null, WhmClips.durationMillis("whm_vo_nope"))
    }

    @Test
    fun `ret_11 is the re-recorded clip of about 5_4 seconds`() {
        val millis = requireNotNull(WhmClips.durationMillis("whm_vo_ret_11"))

        assertTrue("ret_11 was $millis ms", millis in 5_300L..5_600L)
    }
}
