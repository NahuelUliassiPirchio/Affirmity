package com.pirxhio.affirmity.meditation.wimhof

import com.pirxhio.affirmity.data.catalog.CatalogLocale
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
            setOf(WimHofVariables.ROUNDS, WimHofVariables.BREATHS_PER_ROUND, WimHofVariables.VOICE_LOCALE),
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
        assertTrue(WhmVoicePolicy.REC_CUENTA_AT_MILLIS + ms(WhmVoicePolicy.recCuenta(CatalogLocale.ES)) <= fixed(phase(voice, WimHofPhaseIds.RECOVERY_HOLD)))
        assertTrue(240_000L + ms("whm_vo_tiempo_400") < fixed(phase(voice, WimHofPhaseIds.EXHALE_HOLD)))
    }

    @Test
    fun `the closing chain is made of the four CIERRE clips`() {
        assertEquals(4, WhmVoicePolicy.CLOSING_CLIPS.size)
        assertTrue(WhmVoicePolicy.CLOSING_CLIPS.all { WhmClips.durationMillis(it) != null })
    }

    // --- English guide: every clip-derived length comes from the English clips -----------------

    private val english = WimHofConfig(voiceGuide = true, voiceLocale = CatalogLocale.EN)

    @Test
    fun `the definition records which locale's clips the director must play`() {
        assertEquals("es", wimHofMeditationDefinition(voice).variables[WimHofVariables.VOICE_LOCALE])
        assertEquals("en", wimHofMeditationDefinition(english).variables[WimHofVariables.VOICE_LOCALE])
    }

    @Test
    fun `English phases are sized from the English clips and Spanish stays as it was`() {
        val root = wimHofMeditationDefinition(english).root as MeditationSequence

        assertEquals(WhmVoicePolicy.introMillis(CatalogLocale.EN), fixed(root.children.first() as Phase))
        assertEquals(24_500L, fixed(root.children.first() as Phase))
        assertEquals(WhmVoicePolicy.closingMillis(CatalogLocale.EN), fixed(root.children.last() as Phase))
        assertEquals(18_500L, fixed(root.children.last() as Phase))
        assertEquals(WhmVoicePolicy.roundIntroMillis(CatalogLocale.EN), fixed(phase(english, WimHofPhaseIds.ROUND_INTRO)))
        assertEquals(WhmVoicePolicy.recoveryVoiceMillis(CatalogLocale.EN), fixed(phase(english, WimHofPhaseIds.RECOVERY_HOLD)))
        assertEquals(16_400L, fixed(phase(english, WimHofPhaseIds.RECOVERY_HOLD)))

        // Spanish is unchanged.
        assertEquals(25_500L, fixed((wimHofMeditationDefinition(voice).root as MeditationSequence).children.first() as Phase))
        assertEquals(20_000L, fixed((wimHofMeditationDefinition(voice).root as MeditationSequence).children.last() as Phase))
        assertEquals(16_400L, fixed(phase(voice, WimHofPhaseIds.RECOVERY_HOLD)))
    }

    @Test
    fun `English intro fits its chained clips and the English hold fits TIEMPO_400`() {
        val en = com.pirxhio.affirmity.meditation.wimhof.voice.WhmVoiceClips.of(CatalogLocale.EN)
        val needed = WhmVoicePolicy.introClips(CatalogLocale.EN).sumOf { en.durationMillis(it)!! } + 2 * WhmVoicePolicy.CHAIN_GAP_MILLIS
        assertTrue(needed < fixed(phase(english, WimHofPhaseIds.INTRO)))
        assertTrue(240_000L + en.durationMillis("whm_vo_en_tiempo_400")!! < fixed(phase(english, WimHofPhaseIds.EXHALE_HOLD)))
    }
}
