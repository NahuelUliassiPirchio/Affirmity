package com.pirxhio.affirmity.meditation.wimhof

import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.MeditationNode
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.PlayCue
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.audio.MeditationCue
import com.pirxhio.affirmity.meditation.wimhof.voice.WhmClips
import com.pirxhio.affirmity.meditation.wimhof.voice.WhmVoicePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WimHofVoiceGuideDefinitionTest {

    private fun walk(node: MeditationNode): List<MeditationNode> = listOf(node) + when (node) {
        is MeditationSequence -> node.children.flatMap(::walk)
        is Repeat -> walk(node.child)
        else -> emptyList()
    }

    private fun phases(config: WimHofConfig): List<Phase> =
        walk(wimHofMeditationDefinition(config).root).filterIsInstance<Phase>()

    private fun phase(config: WimHofConfig, id: String): Phase = phases(config).first { it.id == id }

    private val voice = WimHofConfig(voiceGuide = true)

    private fun fixed(p: Phase): Long = (p.duration as PhaseDuration.Fixed).millis

    @Test
    fun `voice guide authors a skippable 25_500 ms intro before the rounds`() {
        val root = wimHofMeditationDefinition(voice).root as MeditationSequence
        val intro = root.children.first() as Phase

        assertEquals(WimHofPhaseIds.INTRO, intro.id)
        assertEquals(25_500L, fixed(intro))
        assertTrue(MeditationEvent.UserAction::class in intro.exitEvents)
        assertTrue(root.children[1] is Repeat)
    }

    @Test
    fun `voice guide opens every round with an 8_800 ms round intro that rings the bowl`() {
        val round = ((wimHofMeditationDefinition(voice).root as MeditationSequence).children[1] as Repeat).child as MeditationSequence
        val first = round.children.first() as Phase

        assertEquals(WimHofPhaseIds.ROUND_INTRO, first.id)
        assertEquals(8_800L, fixed(first))
        assertTrue(first.onEnter.any { it is PlayCue && it.cue == MeditationCue.BreathworkBowl })
        assertEquals(
            listOf(WimHofPhaseIds.ROUND_INTRO, WimHofPhaseIds.BREATHING, WimHofPhaseIds.EXHALE_HOLD, WimHofPhaseIds.RECOVERY_HOLD),
            round.children.map { it.id },
        )
    }

    @Test
    fun `the bowl replaces the breathing entry cue when the round intro exists`() {
        val inhale = phase(voice, WimHofPhaseIds.INHALE)
        assertFalse(inhale.onEnter.any { it is PlayCue && it.cue == MeditationCue.RoundStart })
        val plainInhale = phase(WimHofConfig(), WimHofPhaseIds.INHALE)
        assertTrue(plainInhale.onEnter.any { it is PlayCue && it.cue == MeditationCue.RoundStart })
    }

    @Test
    fun `voice guide ends with a skippable closing phase sized from the CIERRE clips`() {
        val root = wimHofMeditationDefinition(voice).root as MeditationSequence
        val closing = root.children.last() as Phase

        assertEquals(WimHofPhaseIds.CLOSING, closing.id)
        assertEquals(WhmVoicePolicy.closingMillis(), fixed(closing))
        assertTrue(fixed(closing) >= WhmVoicePolicy.CLOSING_CLIPS.sumOf(::ms) + 3 * WhmVoicePolicy.CHAIN_GAP_MILLIS)
        assertTrue(MeditationEvent.UserAction::class in closing.exitEvents)
        assertNull(phases(WimHofConfig()).firstOrNull { it.id == WimHofPhaseIds.CLOSING })
    }

    @Test
    fun `recovery is 16_400 ms with the voice guide and 15_000 ms without`() {
        assertEquals(16_400L, fixed(phase(voice, WimHofPhaseIds.RECOVERY_HOLD)))
        assertEquals(15_000L, fixed(phase(WimHofConfig(), WimHofPhaseIds.RECOVERY_HOLD)))
    }

    @Test
    fun `voice off keeps today's tree`() {
        val config = WimHofConfig()
        assertTrue(wimHofMeditationDefinition(config).root is Repeat)
        assertNull(phases(config).firstOrNull { it.id == WimHofPhaseIds.INTRO })
        assertNull(phases(config).firstOrNull { it.id == WimHofPhaseIds.ROUND_INTRO })
    }

    @Test
    fun `shared ids are what the definition really uses`() {
        val definition = wimHofMeditationDefinition(voice)
        assertEquals(
            setOf(WimHofVariables.ROUNDS, WimHofVariables.BREATHS_PER_ROUND),
            definition.variables.keys,
        )
        val repeats = walk(definition.root).filterIsInstance<Repeat>().map { it.id }
        assertTrue(WimHofPhaseIds.ROUNDS in repeats)
        assertTrue(WimHofPhaseIds.BREATHING in repeats)
        assertNotNull(phases(voice).firstOrNull { it.id == WimHofPhaseIds.INHALE })
        assertEquals(
            listOf("rounds", "breathing", "inhale", "intro", "round_intro", "closing", "exhale_hold", "recovery_hold"),
            listOf(
                WimHofPhaseIds.ROUNDS, WimHofPhaseIds.BREATHING, WimHofPhaseIds.INHALE, WimHofPhaseIds.INTRO,
                WimHofPhaseIds.ROUND_INTRO, WimHofPhaseIds.CLOSING, WimHofPhaseIds.EXHALE_HOLD, WimHofPhaseIds.RECOVERY_HOLD,
            ),
        )
    }

    // --- Phase durations fit their clips ---------------------------------------------------------

    private fun ms(id: String): Long = WhmClips.durationMillis(id)!!

    @Test
    fun `the intro fits its three chained clips`() {
        val needed = WhmVoicePolicy.INTRO_CLIPS.sumOf(::ms) + 2 * WhmVoicePolicy.CHAIN_GAP_MILLIS
        assertTrue("$needed", needed < fixed(phase(voice, WimHofPhaseIds.INTRO)))
    }

    @Test
    fun `the round intro fits ENTRE and the end-aligned RONDA clip`() {
        val roundIntro = fixed(phase(voice, WimHofPhaseIds.ROUND_INTRO))
        val longestEntre = (1..WhmVoicePolicy.ENTRE_CLIP_COUNT).maxOf { ms(WhmVoicePolicy.entreClipId(it)) }
        listOf("whm_vo_ronda_1", "whm_vo_ronda_2", "whm_vo_ronda_3", "whm_vo_ronda_final").forEach { ronda ->
            val start = WhmVoicePolicy.rondaStartMillis(ronda)
            assertTrue("$ronda ends inside the phase", start + ms(ronda) <= roundIntro)
            assertTrue("$ronda starts after the longest ENTRE", WhmVoicePolicy.ENTRE_AT_MILLIS + longestEntre <= start)
        }
    }

    @Test
    fun `the recovery fits REC_CUENTA and the hold fits TIEMPO_400`() {
        assertTrue(WhmVoicePolicy.REC_CUENTA_AT_MILLIS + ms(WhmVoicePolicy.REC_CUENTA) <= fixed(phase(voice, WimHofPhaseIds.RECOVERY_HOLD)))
        assertTrue(240_000L + ms("whm_vo_tiempo_400") < fixed(phase(voice, WimHofPhaseIds.EXHALE_HOLD)))
    }

    @Test
    fun `the closing chain is made of the four CIERRE clips`() {
        assertEquals(4, WhmVoicePolicy.CLOSING_CLIPS.size)
        assertTrue(WhmVoicePolicy.CLOSING_CLIPS.all { WhmClips.durationMillis(it) != null })
    }
}
