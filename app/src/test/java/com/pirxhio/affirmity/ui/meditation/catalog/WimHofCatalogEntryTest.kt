package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationNode
import com.pirxhio.affirmity.meditation.MeditationRuntimeState
import com.pirxhio.affirmity.meditation.wimhof.voice.WhmClips
import com.pirxhio.affirmity.meditation.wimhof.voice.WhmVoiceDirector
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.customization.CustomizationField
import com.pirxhio.affirmity.meditation.customization.defaultValues
import org.junit.Assert.assertTrue
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.customization.resolvedValues
import com.pirxhio.affirmity.meditation.wimhof.WimHofBreathSpeed
import com.pirxhio.affirmity.meditation.wimhof.WimHofPhaseIds
import org.junit.Assert.assertEquals
import org.junit.Test

class WimHofCatalogEntryTest {

    private val entry = requireNotNull(findMeditationCatalogEntry("wim_hof"))

    private fun walk(node: MeditationNode): List<MeditationNode> = listOf(node) + when (node) {
        is Repeat -> walk(node.child)
        is MeditationSequence -> node.children.flatMap(::walk)
        is Phase -> emptyList()
    }

    @Test
    fun `counter repeat ids exist in the definition tree`() {
        val repeatIds = walk(entry.definition(emptyMap()).root).filterIsInstance<Repeat>().map { it.id }.toSet()

        entry.presentation.counters.forEach { assertEquals(true, it.repeatId in repeatIds) }
        assertEquals(setOf("rounds", "breathing"), entry.presentation.counters.map { it.repeatId }.toSet())
    }

    @Test
    fun `every ShowText id in the tree has a textResources entry`() {
        val textIds = walk(entry.definition(emptyMap()).root)
            .filterIsInstance<Phase>()
            .flatMap { it.onEnter }
            .filterIsInstance<ShowText>()
            .map { it.textId }
            .toSet()

        assertEquals(textIds, entry.presentation.textResources.keys)
    }

    @Test
    fun `manual releases target the exhale hold phase`() {
        assertEquals(listOf(WimHofPhaseIds.EXHALE_HOLD), entry.presentation.manualReleases.map { it.phaseId })
    }

    @Test
    fun `an off-list persisted breathsPerRound falls back to the default instead of crashing`() {
        listOf("25", "0", "-3", "abc").forEach { bad ->
            val resolved = resolvedValues(entry.customizationFields, mapOf("breathsPerRound" to bad))

            // Must not throw the `require(breathsPerRound > 0)` IllegalArgumentException.
            val definition = entry.definition(resolved)
            assertEquals(com.pirxhio.affirmity.meditation.wimhof.WimHofConfig.DEFAULT_BREATHS_PER_ROUND, definition.variables["breathsPerRound"])
        }
    }

    // --- breath speed + breath sound ----------------------------------------------------------------

    private fun breathDurations(config: Map<String, String>): Pair<Long, Long> {
        val phases = walk(entry.definition(config).root).filterIsInstance<Phase>()
        fun fixed(id: String) = (phases.first { it.id == id }.duration as PhaseDuration.Fixed).millis
        return fixed("inhale") to fixed("exhale")
    }

    private fun sfxIdsIn(config: Map<String, String>): List<String> =
        walk(entry.definition(config).root).filterIsInstance<Phase>()
            .flatMap { it.onEnter }.filterIsInstance<PlayAudio>().map { it.audioId }

    @Test
    fun `breathSpeed is a slow-normal-fast option defaulting to normal`() {
        val field = entry.customizationFields.filterIsInstance<CustomizationField.Options<*>>().first { it.key == "breathSpeed" }

        assertEquals(listOf("slow", "normal", "fast"), field.options)
        assertEquals("normal", field.default)
        assertEquals("normal", defaultValues(entry.customizationFields)["breathSpeed"])
    }

    @Test
    fun `breathSound is a toggle defaulting to on`() {
        val field = entry.customizationFields.filterIsInstance<CustomizationField.Toggle>().first { it.key == "breathSound" }

        assertEquals(true, field.default)
        assertEquals("true", defaultValues(entry.customizationFields)["breathSound"])
    }

    @Test
    fun `the selected speed drives the phase durations`() {
        assertEquals(2_000L to 2_000L, breathDurations(mapOf("breathSpeed" to "slow")))
        assertEquals(1_700L to 1_700L, breathDurations(mapOf("breathSpeed" to "normal")))
        assertEquals(1_200L to 1_000L, breathDurations(mapOf("breathSpeed" to "fast")))
    }

    @Test
    fun `missing or off-list persisted speed falls back to normal`() {
        listOf(emptyMap(), mapOf("breathSpeed" to "turbo"), mapOf("breathSpeed" to "")).forEach {
            assertEquals(1_700L to 1_700L, breathDurations(it))
        }
    }

    @Test
    fun `speed and breath sound persist in the same config map as rounds`() {
        val resolved = resolvedValues(
            entry.customizationFields,
            mapOf("rounds" to "2", "breathSpeed" to "slow", "breathSound" to "false"),
        )

        assertEquals("2", resolved["rounds"])
        assertEquals("slow", resolved["breathSpeed"])
        assertEquals("false", resolved["breathSound"])
    }

    @Test
    fun `breathSound off removes every sfx and the default keeps them`() {
        assertTrue(sfxIdsIn(emptyMap()).isNotEmpty())
        assertEquals(emptyList<String>(), sfxIdsIn(mapOf("breathSound" to "false")))
    }

    @Test
    fun `every sfx id in the tree has a sfxResources entry and nothing else is mapped`() {
        val used = WimHofBreathSpeed.entries.flatMap {
            sfxIdsIn(mapOf("breathSpeed" to it.key))
        }.toSet()

        assertEquals(used, entry.presentation.sfxResources.keys)
    }

    @Test
    fun `the exhale hold ceiling is 250 seconds`() {
        val hold = walk(entry.definition(emptyMap()).root).filterIsInstance<Phase>().first { it.id == WimHofPhaseIds.EXHALE_HOLD }

        assertEquals(PhaseDuration.Fixed(250_000L), hold.duration)
    }

    @Test
    fun `double tap advances only on wim_hof`() {
        assertTrue(entry.presentation.doubleTapAdvances)
        meditationCatalog().filter { it.id != "wim_hof" }.forEach {
            assertEquals("${it.id}: doubleTapAdvances must be off", false, it.presentation.doubleTapAdvances)
        }
    }

    @Test
    fun `the typical hold estimate yields a ten minute declared length`() {
        assertEquals(10, entry.approxDurationMinutes)
        val expected = expectedDurationMillis(entry.definition(emptyMap()), entry.approxDurationMinutes)
        // 3 rounds x (30 breaths x 3.4 s + 90 s typical hold + 15 s recovery)
        assertEquals(3 * (102_000L + 90_000L + 15_000L), expected)
    }

    @Test
    fun `only wim_hof releases voice players after play`() {
        assertTrue(entry.presentation.releaseVoiceAfterPlay)
        meditationCatalog().filter { it.id != "wim_hof" }.forEach {
            assertEquals("${it.id}: releaseVoiceAfterPlay must be off", false, it.presentation.releaseVoiceAfterPlay)
        }
    }

    @Test
    fun `every voice clip has a raw resource mapped under its own id`() {
        assertEquals(WhmClips.voiceMillis.keys, entry.presentation.audioResources.keys)
    }

    @Test
    fun `voice guide is a Spanish-only toggle that defaults on`() {
        val toggle = entry.customizationFields.filterIsInstance<CustomizationField.Toggle>().single { it.key == "voiceGuide" }

        assertEquals(true, toggle.default)
        assertEquals(setOf("es"), toggle.localeTags)
        assertEquals("true", defaultValues(entry.customizationFields)["voiceGuide"])
    }

    @Test
    fun `only wim_hof provides a session director`() {
        val sink = object : MeditationCommandExecutor {
            override fun execute(command: com.pirxhio.affirmity.meditation.MeditationCommand) = Unit
        }
        assertTrue(entry.presentation.sessionDirectorFactory!!(sink) { MeditationRuntimeState() } is WhmVoiceDirector)
        meditationCatalog().filter { it.id != "wim_hof" }.forEach {
            assertEquals("${it.id}: no director", null, it.presentation.sessionDirectorFactory)
        }
    }
}
