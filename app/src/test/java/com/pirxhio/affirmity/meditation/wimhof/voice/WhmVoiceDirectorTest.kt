package com.pirxhio.affirmity.meditation.wimhof.voice

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.PlayAudio
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

class WhmVoiceDirectorTest {

    private fun config(breaths: Int = 30, rounds: Int = 3) =
        WimHofConfig(rounds = rounds, breathsPerRound = breaths)

    private fun audioCommands(h: WhmSessionHarness): List<MeditationCommand> =
        h.commands.filter { it is PlayAudio || it is StopAudio || it is PlayVoice }

    /** Runs a whole breathing block (clips finish right away) and lands in the exhale hold, where
     * RET_INICIO is left playing. */
    private fun enterHold(h: WhmSessionHarness, breaths: Int) {
        h.start()
        h.completeBreaths(breaths - 1)
        h.completePhase()
        h.completePhase()
        assertEquals(WimHofPhaseIds.EXHALE_HOLD, h.phaseId)
    }

    // --- Breathing: countdown ----------------------------------------------------------------

    @Test
    fun `CUENTA_5 when five breaths remain and CUENTA_ULTIMA on the last breath for 20 30 and 40`() {
        listOf(20, 30, 40).forEach { n ->
            val h = WhmSessionHarness(config(breaths = n), random = Random(n))
            h.start()
            h.completeBreaths(n - 1)

            val five = h.voicePlays.filter { it.id == "whm_vo_cuenta_5" }
            val last = h.voicePlays.filter { it.id == "whm_vo_cuenta_ultima" }
            assertEquals("n=$n cuenta_5", listOf(n - 5), five.map { it.breathIndex })
            assertEquals("n=$n cuenta_ultima", listOf(n - 1), last.map { it.breathIndex })
        }
    }

    // --- Breathing: RESP ----------------------------------------------------------------------

    @Test
    fun `RESP clips come every 4 to 6 cycles, never repeat back to back and stop before the last 8 breaths`() {
        (0L..20L).forEach { seed ->
            val h = WhmSessionHarness(config(breaths = 40), random = Random(seed))
            h.start()
            h.completeBreaths(39)

            val resp = h.voicePlays.filter { it.id.startsWith("whm_vo_resp_") }
            val indices = resp.map { it.breathIndex!! }
            assertTrue("seed=$seed has several RESP clips: $indices", resp.size >= 4)
            assertTrue("seed=$seed first RESP at or after cycle 4: $indices", indices.first() >= 4)
            indices.zipWithNext().forEach { (a, b) ->
                assertTrue("seed=$seed gap $a->$b in 4..6", b - a in 4..6)
            }
            assertTrue("seed=$seed no RESP in the last 8 breaths: $indices", indices.all { it < 40 - 8 })
            resp.map { it.id }.zipWithNext().forEach { (a, b) -> assertTrue("seed=$seed repeats $a", a != b) }
            assertTrue("seed=$seed overlaps=${h.overlaps}", h.overlaps.isEmpty())
        }
    }

    @Test
    fun `RESP is skipped while the previous voice clip is still playing and resumes afterwards`() {
        val h = WhmSessionHarness(config(breaths = 30), random = ScriptedRandom())
        h.start()
        h.completeBreaths(4, completeVoices = false) // cycle 4 due -> plays
        val first = h.voicePlays.single { it.id.startsWith("whm_vo_resp_") }
        assertEquals(4, first.breathIndex)

        h.completeBreaths(4, completeVoices = false) // cycle 8 due, but the first clip never ended
        assertEquals(1, h.voicePlays.count { it.id.startsWith("whm_vo_resp_") })

        h.audioCompleted(first.id)
        h.completeBreaths(4, completeVoices = false) // cycle 12 due and the slot is free
        val resp = h.voicePlays.filter { it.id.startsWith("whm_vo_resp_") }
        assertEquals(listOf(4, 12), resp.map { it.breathIndex })
        assertTrue(resp[0].id != resp[1].id)
    }

    // --- Hold --------------------------------------------------------------------------------

    @Test
    fun `entering the hold frees the slot, then plays the SFX and RET_INICIO without waiting for a stuck clip`() {
        val h = WhmSessionHarness(config(breaths = 20), random = Random(2))
        h.start()
        h.completeBreaths(18)
        h.completeBreaths(1, completeVoices = false) // CUENTA_ULTIMA now playing and never acknowledged
        assertEquals("whm_vo_cuenta_ultima", h.playing)

        h.completePhase()
        h.completePhase()

        assertEquals(WimHofPhaseIds.EXHALE_HOLD, h.phaseId)
        assertEquals(
            listOf(
                PlayAudio("whm_sfx_exhale_hold"),
                StopAudio("whm_vo_cuenta_ultima"),
                PlayVoice("whm_vo_ret_inicio", duckAmbientTo = null),
            ),
            audioCommands(h).takeLast(3),
        )
        assertTrue(h.overlaps.isEmpty())
    }

    @Test
    fun `TIEMPO clips play at 30 to 240 seconds, TIEMPO_400 under the 250 second ceiling`() {
        val h = WhmSessionHarness(config(breaths = 20), random = Random(3))
        enterHold(h, 20)
        h.tickTo(240_000)

        val tiempo = h.voicePlays.filter { it.id.startsWith("whm_vo_tiempo_") }
        assertEquals(WhmClips.tiempoBySeconds.toSortedMap().values.toList(), tiempo.map { it.id })
        WhmClips.tiempoBySeconds.toSortedMap().entries.zip(tiempo).forEach { (mark, play) ->
            assertTrue(
                "${play.id} at ${play.atMillis} for mark ${mark.key}s",
                play.atMillis in mark.key * 1000L..mark.key * 1000L + 250,
            )
        }
        assertEquals("whm_vo_tiempo_400", tiempo.last().id)
        assertTrue(240_000 < WimHofConfig.DEFAULT_EXHALE_HOLD_MILLIS)
        assertTrue(h.overlaps.isEmpty())
    }

    @Test
    fun `RET clips follow RET_INICIO at least 20 seconds apart and never overlap`() {
        val h = WhmSessionHarness(config(breaths = 20), random = Random(11))
        enterHold(h, 20)
        h.tickTo(240_000)

        assertEquals("whm_vo_ret_inicio", h.voicePlays.first { it.id.startsWith("whm_vo_ret_") }.id)
        val ret = h.voicePlays.filter { it.id.startsWith("whm_vo_ret_") && it.id != "whm_vo_ret_inicio" }
        assertTrue("several RET clips: $ret", ret.size >= 3)
        assertTrue(ret.first().atMillis >= 20_000)
        ret.zipWithNext().forEach { (a, b) -> assertTrue("${a.atMillis}->${b.atMillis}", b.atMillis - a.atMillis >= 20_000) }
        assertTrue(h.overlaps.isEmpty())
    }

    @Test
    fun `a due TIEMPO waits for a RET that is still playing`() {
        val h = WhmSessionHarness(
            config(breaths = 20),
            random = ScriptedRandom { from, _ -> if (from == 20_000) 27_000 else from },
        )
        enterHold(h, 20)
        h.tickTo(5_000)
        h.tickTo(30_000, autoFinish = false)

        // RET at 27 s fits before the 30 s mark by clip length, but its end never arrived.
        assertEquals(listOf("whm_vo_ret_inicio", "whm_vo_ret_01"), h.voiceIds.takeLast(2))
        assertEquals("whm_vo_ret_01", h.playing)

        h.audioCompleted("whm_vo_ret_01")

        assertEquals("whm_vo_tiempo_030", h.voiceIds.last())
        assertTrue(h.overlaps.isEmpty())
    }

    @Test
    fun `an optional RET is skipped when it would run into the next time mark`() {
        val h = WhmSessionHarness(
            config(breaths = 20),
            random = ScriptedRandom { from, _ -> if (from == 20_000) 29_000 else from },
        )
        enterHold(h, 20)
        h.tickTo(30_000)

        assertEquals(listOf("whm_vo_ret_inicio", "whm_vo_tiempo_030"), h.voiceIds.takeLast(2))
        assertEquals(1, h.voiceIds.count { it.startsWith("whm_vo_ret_") })
    }

    @Test
    fun `a tap at 45 seconds cancels pending hold cues and plays no later TIEMPO or RET`() {
        val h = WhmSessionHarness(
            config(breaths = 20),
            random = ScriptedRandom { from, _ -> if (from == 20_000) 27_000 else from },
        )
        enterHold(h, 20)
        h.tickTo(5_000)
        h.tickTo(30_000, autoFinish = false) // RET_01 plays at 27 s, TIEMPO_030 is queued behind it
        h.tickTo(45_000, autoFinish = false)
        assertEquals("whm_vo_ret_01", h.playing)

        h.next()

        assertEquals(WimHofPhaseIds.RECOVERY_HOLD, h.phaseId)
        assertEquals(
            listOf(StopAudio("whm_vo_ret_01"), PlayVoice("whm_vo_rec_inicio", duckAmbientTo = null)),
            audioCommands(h).filter { it is StopAudio || it is PlayVoice }.takeLast(2),
        )
        h.audioCompleted("whm_vo_ret_01") // stale: that clip was cut
        h.finishVoices()
        h.tickTo(15_000)

        assertFalse(h.voiceIds.contains("whm_vo_tiempo_030"))
        assertEquals(listOf("whm_vo_rec_inicio", "whm_vo_rec_cuenta"), h.voiceIds.takeLast(2))
    }

    // --- Recovery ----------------------------------------------------------------------------

    @Test
    fun `recovery plays the SFX and REC_INICIO, then REC_CUENTA exactly at 12720 ms`() {
        val h = WhmSessionHarness(config(breaths = 20), random = Random(4))
        enterHold(h, 20)
        h.tickTo(5_000)
        h.next()

        assertEquals(WimHofPhaseIds.RECOVERY_HOLD, h.phaseId)
        assertEquals(
            listOf(PlayAudio("whm_sfx_inhale_recovery"), PlayVoice("whm_vo_rec_inicio", duckAmbientTo = null)),
            audioCommands(h).filter { it is PlayAudio || it is PlayVoice }.takeLast(2),
        )
        h.tickTo(12_719)
        assertFalse(h.voiceIds.contains("whm_vo_rec_cuenta"))
        h.tickTo(12_720)
        assertEquals("whm_vo_rec_cuenta", h.voiceIds.last())
        assertEquals(12_720L, h.voicePlays.last().atMillis)
    }

    // --- Engine filtering mirrored --------------------------------------------------------------

    @Test
    fun `ticks and completions from an older timer generation are dropped`() {
        val h = WhmSessionHarness(config(breaths = 20), random = Random(5))
        enterHold(h, 20)
        val holdGeneration = h.generation
        h.tickTo(5_000)
        h.next()
        val before = h.voiceIds

        h.rawTick(holdGeneration, 60_000) // would be TIEMPO_100 if it leaked into recovery
        h.rawCompleted(holdGeneration)

        assertEquals(before, h.voiceIds)
        assertEquals(WimHofPhaseIds.RECOVERY_HOLD, h.phaseId)
    }

    @Test
    fun `ticks while paused are ignored and marks fire after resume`() {
        val h = WhmSessionHarness(config(breaths = 20), random = Random(6))
        enterHold(h, 20)
        h.tickTo(4_000)

        h.pause()
        h.rawTick(h.generation, 30_000)
        assertFalse(h.voiceIds.contains("whm_vo_tiempo_030"))

        h.resume()
        h.tick(30_000)
        assertTrue(h.voiceIds.contains("whm_vo_tiempo_030"))
    }

    @Test
    fun `the session ending resets the director so later ticks play nothing`() {
        val h = WhmSessionHarness(config(breaths = 20), random = Random(7))
        enterHold(h, 20)
        h.tickTo(4_000)
        val before = h.voiceIds

        h.cancel()
        h.rawTick(h.generation, 30_000)

        assertEquals(SessionStatus.Cancelled, h.state.status)
        assertEquals(before, h.voiceIds)
    }

    // --- Slot / reentrancy ---------------------------------------------------------------------

    @Test
    fun `an AudioCompleted raised synchronously from PlayVoice is handled once, after the emission`() {
        val h = WhmSessionHarness(config(breaths = 20), random = Random(8), completeVoiceImmediately = true)
        enterHold(h, 20)
        assertEquals(1, h.voiceIds.count { it == "whm_vo_ret_inicio" })

        h.tick(30_000)
        h.tick(60_000)
        // Two marks due in one tick: the second is queued and starts after the first completes.
        h.tick(130_000)

        val tiempo = h.voiceIds.filter { it.startsWith("whm_vo_tiempo_") }
        assertEquals(
            listOf("whm_vo_tiempo_030", "whm_vo_tiempo_100", "whm_vo_tiempo_130", "whm_vo_tiempo_200"),
            tiempo,
        )
        assertTrue(h.overlaps.isEmpty())
    }

    @Test
    fun `an engine StopAudio frees the slot so the next required clip plays without AudioCompleted`() {
        val h = WhmSessionHarness(config(breaths = 20), random = Random(9))
        enterHold(h, 20)
        assertEquals("whm_vo_ret_inicio", h.playing)

        h.director!!.execute(StopAudio())
        h.tick(30_000)

        assertEquals("whm_vo_tiempo_030", h.voiceIds.last())
    }

    @Test
    fun `a full two round session never overlaps voice clips`() {
        (0L..10L).forEach { seed ->
            val h = WhmSessionHarness(config(breaths = 20, rounds = 2), random = Random(seed))
            h.start()
            repeat(2) {
                h.completeBreaths(20)
                h.tickTo(100_000)
                h.next()
                h.tickTo(14_900)
                h.completePhase()
            }

            assertEquals("seed=$seed", SessionStatus.Completed, h.state.status)
            assertTrue("seed=$seed ${h.overlaps}", h.overlaps.isEmpty())
        }
    }

    // --- Skip the breathing block ------------------------------------------------------------

    @Test
    fun `Skip mid-block cuts the playing cue, starts the hold voice and never counts down`() {
        val h = WhmSessionHarness(config(breaths = 20, rounds = 2), random = Random(3))
        h.start()
        h.completeBreaths(14)
        h.completePhase()
        h.finishVoices()
        h.completePhase() // breath 15 inhale: CUENTA_5
        assertEquals("whm_vo_cuenta_5", h.playing)

        h.next()

        assertEquals(WimHofPhaseIds.EXHALE_HOLD, h.phaseId)
        assertEquals(
            listOf(StopAudio("whm_vo_cuenta_5"), PlayVoice("whm_vo_ret_inicio", duckAmbientTo = null)),
            audioCommands(h).filter { it is StopAudio || it is PlayVoice }.takeLast(2),
        )
        assertEquals("whm_vo_ret_inicio", h.playing)
        assertFalse(h.voiceIds.contains("whm_vo_cuenta_ultima"))
        assertTrue(h.overlaps.isEmpty())
    }

    @Test
    fun `Skip on the very last breath cuts CUENTA_ULTIMA and the hold cues start normally`() {
        val h = WhmSessionHarness(config(breaths = 20, rounds = 2), random = Random(3))
        h.start()
        h.completeBreaths(18)
        h.completeBreaths(1, completeVoices = false) // breath 20 inhale: CUENTA_ULTIMA playing
        assertEquals("whm_vo_cuenta_ultima", h.playing)

        h.next()

        assertEquals(WimHofPhaseIds.EXHALE_HOLD, h.phaseId)
        assertEquals(
            listOf(StopAudio("whm_vo_cuenta_ultima"), PlayVoice("whm_vo_ret_inicio", duckAmbientTo = null)),
            audioCommands(h).filter { it is StopAudio || it is PlayVoice }.takeLast(2),
        )
        assertEquals(1, h.voiceIds.count { it == "whm_vo_cuenta_ultima" })
        assertEquals("whm_vo_ret_inicio", h.playing)

        h.audioCompleted("whm_vo_ret_inicio")
        h.tickTo(30_000)
        assertTrue(h.voiceIds.any { it.startsWith("whm_vo_tiempo_") })
        assertTrue(h.overlaps.isEmpty())
    }

    @Test
    fun `after a skipped block the next round counts down from a clean state`() {
        val h = WhmSessionHarness(config(breaths = 20, rounds = 2), random = Random(3))
        h.start()
        h.completeBreaths(14)
        h.completePhase()
        h.finishVoices()
        h.completePhase()
        h.next() // skip the block at breath 15
        h.tickTo(5_000)
        h.next() // hold -> recovery
        h.tickTo(14_900)
        h.completePhase() // recovery -> round 2 intro
        h.tickTo(8_800)
        h.completePhase()
        assertEquals(0, h.breathingIndex)
        val before = h.voicePlays.size

        h.completeBreaths(19)

        val round2 = h.voicePlays.drop(before)
        assertEquals(listOf(15), round2.filter { it.id == "whm_vo_cuenta_5" }.map { it.breathIndex })
        assertEquals(listOf(19), round2.filter { it.id == "whm_vo_cuenta_ultima" }.map { it.breathIndex })
        assertTrue(h.overlaps.isEmpty())
    }

    // --- Voice off ---------------------------------------------------------------------------

    @Test
    fun `without a director there is no voice and the breath SFX are unaffected`() {
        val h = WhmSessionHarness(config(breaths = 20), voice = false)
        enterHold(h, 20)
        h.tickTo(60_000)

        assertTrue(h.voicePlays.isEmpty())
        assertTrue(h.commands.contains(PlayAudio("whm_sfx_exhale_hold")))
        assertTrue(h.commands.contains(PlayAudio("whm_sfx_inhale_normal")))
    }

    @Test
    fun `a session that names no known clip locale stays silent and never falls back to Spanish`() {
        listOf(emptyMap<String, Any?>(), mapOf<String, Any?>("voiceLocale" to "fr")).forEach { variables ->
            val played = mutableListOf<MeditationCommand>()
            val sink = object : com.pirxhio.affirmity.meditation.MeditationCommandExecutor {
                override fun execute(command: MeditationCommand) { played += command }
            }
            val state = com.pirxhio.affirmity.meditation.MeditationRuntimeState(
                status = SessionStatus.Running,
                activePath = listOf(WimHofPhaseIds.INTRO),
                variables = variables,
            )
            val director = WhmVoiceDirector(sink, { state })
            director.execute(com.pirxhio.affirmity.meditation.StartTimer(25_500L, 1))
            director.onEvent(com.pirxhio.affirmity.meditation.MeditationEvent.TimerTick(1, 1_000L, null))

            assertTrue("$variables: $played", played.none { it is PlayVoice })
        }
    }
}
