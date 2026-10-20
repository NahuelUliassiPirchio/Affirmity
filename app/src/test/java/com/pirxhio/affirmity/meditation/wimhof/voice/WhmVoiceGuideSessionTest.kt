package com.pirxhio.affirmity.meditation.wimhof.voice

import com.pirxhio.affirmity.meditation.EndSession
import com.pirxhio.affirmity.meditation.PlayVoice
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.StopAudio
import com.pirxhio.affirmity.meditation.wimhof.WimHofConfig
import com.pirxhio.affirmity.meditation.wimhof.WimHofPhaseIds
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhmVoiceGuideSessionTest {

    private fun harness(rounds: Int = 3, breaths: Int = 20, seed: Long = 1, random: Random = Random(seed)) =
        WhmSessionHarness(WimHofConfig(rounds = rounds, breathsPerRound = breaths, voiceGuide = true), random = random)

    private fun roundIntro(h: WhmSessionHarness) {
        assertEquals(WimHofPhaseIds.ROUND_INTRO, h.phaseId)
        h.tickTo(8_800)
        h.completePhase()
    }

    /** One full round after the intro: round intro, breaths, a 45 s hold ended by a tap, recovery. */
    private fun playRound(h: WhmSessionHarness, breaths: Int) {
        roundIntro(h)
        h.completeBreaths(breaths)
        h.tickTo(45_000)
        h.next()
        h.tickTo(16_300)
    }

    // --- Intro ---------------------------------------------------------------------------------

    @Test
    fun `the intro chains INTRO_1 to INTRO_3 with a 600 ms gap after each clip ends`() {
        val h = harness()
        h.start()
        assertEquals(listOf("whm_vo_intro_1"), h.voiceIds)

        h.tick(7_200)
        h.audioCompleted("whm_vo_intro_1")
        h.tick(7_700)
        assertEquals(listOf("whm_vo_intro_1"), h.voiceIds)
        h.tick(7_800)
        assertEquals(listOf("whm_vo_intro_1", "whm_vo_intro_2"), h.voiceIds)

        h.tick(18_000)
        h.audioCompleted("whm_vo_intro_2")
        h.tick(18_500)
        assertEquals(2, h.voiceIds.size)
        h.tick(18_600)
        assertEquals(listOf("whm_vo_intro_1", "whm_vo_intro_2", "whm_vo_intro_3"), h.voiceIds)
        assertTrue(h.overlaps.isEmpty())
    }

    @Test
    fun `skipping the intro cuts it, plays no more intro clips, and the next session plays it again`() {
        val h = harness()
        h.start()
        h.tick(3_000)
        h.next()

        assertEquals(WimHofPhaseIds.ROUND_INTRO, h.phaseId)
        assertTrue(h.commands.any { it is StopAudio && it.audioId == "whm_vo_intro_1" })
        h.tickTo(8_700)
        assertFalse(h.voiceIds.any { it == "whm_vo_intro_2" || it == "whm_vo_intro_3" })

        val next = harness()
        next.start()
        assertEquals(listOf("whm_vo_intro_1"), next.voiceIds)
    }

    // --- Rounds ----------------------------------------------------------------------------------

    @Test
    fun `three rounds say RONDA_1, RONDA_2 and RONDA_FINAL with ENTRE only after rounds 1 and 2`() {
        val h = harness()
        h.start()
        h.next()
        repeat(3) { playRound(h, 20); h.completePhase() }

        val guide = h.voiceIds.filter { it.startsWith("whm_vo_ronda_") || it.startsWith("whm_vo_entre_") }
        assertEquals(5, guide.size)
        assertEquals("whm_vo_ronda_1", guide[0])
        assertTrue(guide[1].startsWith("whm_vo_entre_"))
        assertEquals("whm_vo_ronda_2", guide[2])
        assertTrue(guide[3].startsWith("whm_vo_entre_"))
        assertEquals("whm_vo_ronda_final", guide[4])
        assertTrue(h.overlaps.isEmpty())
    }

    @Test
    fun `a single round is the final round and has no ENTRE`() {
        val h = harness(rounds = 1)
        h.start()
        h.next()
        roundIntro(h)

        assertEquals(listOf("whm_vo_ronda_final"), h.voiceIds.filter { it.startsWith("whm_vo_ronda_") })
        assertTrue(h.voiceIds.none { it.startsWith("whm_vo_entre_") })
    }

    @Test
    fun `RONDA ends with the round intro and ENTRE comes right after the bowl`() {
        val h = harness()
        h.start()
        h.next()
        playRound(h, 20)
        h.completePhase()
        h.tickTo(8_800)

        val entre = h.voicePlays.first { it.id.startsWith("whm_vo_entre_") }
        val ronda = h.voicePlays.first { it.id == "whm_vo_ronda_2" }
        assertTrue(entre.atMillis in 800L..1_050L)
        val start = WhmVoicePolicy.rondaStartMillis("whm_vo_ronda_2")
        assertTrue(ronda.atMillis in start..(start + 250))
        assertTrue(ronda.atMillis + WhmClips.durationMillis("whm_vo_ronda_2")!! <= 8_800)
    }

    @Test
    fun `ENTRE clips do not repeat back to back`() {
        (0L..15L).forEach { seed ->
            val h = harness(rounds = 4, seed = seed)
            h.start()
            h.next()
            repeat(4) { playRound(h, 20); h.completePhase() }
            val entre = h.voiceIds.filter { it.startsWith("whm_vo_entre_") }
            assertEquals(3, entre.size)
            entre.zipWithNext().forEach { (a, b) -> assertTrue("seed=$seed $entre", a != b) }
        }
    }

    @Test
    fun `recovery with the voice guide plays REC_CUENTA at 12720 inside the 16400 ms phase`() {
        val h = harness(rounds = 1)
        h.start()
        h.next()
        roundIntro(h)
        h.completeBreaths(20)
        h.next()
        h.tickTo(16_300)

        val cuenta = h.voicePlays.first { it.id == "whm_vo_rec_cuenta" }
        assertTrue(cuenta.atMillis in 12_720L..12_970L)
        assertEquals(WimHofPhaseIds.RECOVERY_HOLD, h.phaseId)
    }

    // --- Closing ---------------------------------------------------------------------------------

    private fun toClosing(h: WhmSessionHarness) {
        h.start()
        h.next()
        playRound(h, 20)
        h.completePhase()
        assertEquals(WimHofPhaseIds.CLOSING, h.phaseId)
    }

    @Test
    fun `entering the closing phase plays CIERRE_1 and EndSession plays no voice afterwards`() {
        val h = harness(rounds = 1)
        toClosing(h)

        assertEquals("whm_vo_cierre_1", h.playing)
        h.tickTo(WhmVoicePolicy.closingMillis())
        h.completePhase()

        assertEquals(SessionStatus.Completed, h.state.status)
        val end = h.commands.indexOfFirst { it is EndSession }
        assertTrue(end >= 0)
        assertTrue(h.commands.drop(end).none { it is PlayVoice })
    }

    @Test
    fun `no closing voice is emitted when a round's recovery ends and more rounds remain`() {
        val h = harness(rounds = 2)
        h.start()
        h.next()
        playRound(h, 20)
        h.completePhase()

        assertEquals(WimHofPhaseIds.ROUND_INTRO, h.phaseId)
        assertTrue(h.voiceIds.none { it.startsWith("whm_vo_cierre_") })
    }

    @Test
    fun `the closing chains CIERRE_1 to CIERRE_4 with gaps inside its phase and never overlaps`() {
        val h = harness(rounds = 1)
        toClosing(h)
        h.tickTo(WhmVoicePolicy.closingMillis() - 100)

        assertEquals(WhmVoicePolicy.CLOSING_CLIPS, h.voiceIds.filter { it.startsWith("whm_vo_cierre_") })
        assertEquals(WimHofPhaseIds.CLOSING, h.phaseId)
        assertTrue(h.overlaps.isEmpty())
    }

    @Test
    fun `skipping the closing cuts the voice, and cancelling plays no closing`() {
        val skipped = harness(rounds = 1)
        toClosing(skipped)
        skipped.tick(2_000)
        skipped.next()
        assertEquals(SessionStatus.Completed, skipped.state.status)
        // The executor fades the clip at EndSession; the director adds nothing.
        assertTrue(skipped.commands.dropWhile { it !is EndSession }.none { it is PlayVoice })
        assertEquals(1, skipped.voiceIds.count { it.startsWith("whm_vo_cierre_") })

        val cancelled = harness(rounds = 1)
        cancelled.start()
        cancelled.next()
        playRound(cancelled, 20)
        cancelled.cancel()
        assertTrue(cancelled.voiceIds.none { it.startsWith("whm_vo_cierre_") })
    }

    // --- Breath bookkeeping (carry-over 5.7) ------------------------------------------------------

    @Test
    fun `the RESP gap is drawn again even when a countdown clip takes the due breath`() {
        var respGapDraws = 0
        val random = ScriptedRandom { from, until ->
            if (from == 4 && until == 7) {
                respGapDraws++
                5
            } else {
                from
            }
        }
        val h = WhmSessionHarness(WimHofConfig(rounds = 1, breathsPerRound = 20), random = random)
        h.start()
        h.completeBreaths(19)

        // Draws: breath 0, then the due breaths 5, 10 and 15. Breath 15 is also CUENTA_5.
        assertEquals(4, respGapDraws)
    }
}
