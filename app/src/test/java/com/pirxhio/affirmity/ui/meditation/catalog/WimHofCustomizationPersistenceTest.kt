package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.data.catalog.CatalogLocale
import com.pirxhio.affirmity.data.local.MeditationCustomizationDao
import com.pirxhio.affirmity.data.local.MeditationCustomizationEntity
import com.pirxhio.affirmity.data.repository.RoomMeditationCustomizationRepository
import com.pirxhio.affirmity.meditation.MeditationNode
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PlayAudio
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.customization.resolvedValues
import com.pirxhio.affirmity.meditation.wimhof.WimHofPhaseIds
import com.pirxhio.affirmity.meditation.wimhof.WimHofVariables
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wim Hof's pre-session choices (rounds, breaths, breath speed, breath sound, voice guide) through
 * the real repository -> resolved values -> session customization -> definition chain. Only the
 * Room DAO is replaced by an in-memory one (same contract: one row per meditation id).
 */
class WimHofCustomizationPersistenceTest {

    private class InMemoryDao : MeditationCustomizationDao {
        private val rows = mutableMapOf<String, MeditationCustomizationEntity>()
        override suspend fun getById(meditationId: String) = rows[meditationId]
        override suspend fun upsert(entity: MeditationCustomizationEntity) {
            rows[entity.meditationId] = entity
        }
    }

    private val entry = requireNotNull(findMeditationCatalogEntry("wim_hof"))
    private val repository = RoomMeditationCustomizationRepository(InMemoryDao())

    private fun walk(node: MeditationNode): List<MeditationNode> = listOf(node) + when (node) {
        is Repeat -> walk(node.child)
        is MeditationSequence -> node.children.flatMap(::walk)
        is Phase -> emptyList()
    }

    private suspend fun reload(saved: Map<String, String>?): Map<String, String> {
        if (saved != null) repository.saveValues(entry.id, saved)
        return entry.resolveSessionCustomization(
            resolvedValues(entry.customizationFields, repository.getValues(entry.id)),
            CatalogLocale.ES,
        )
    }

    private fun sfxIds(customization: Map<String, String>): Set<String> =
        walk(entry.definition(customization).root)
            .filterIsInstance<Phase>()
            .flatMap { it.onEnter }
            .filterIsInstance<PlayAudio>()
            .map { it.audioId }
            .filter { it.startsWith("whm_sfx_") }
            .toSet()

    private fun phaseIds(customization: Map<String, String>): List<String> =
        walk(entry.definition(customization).root).filterIsInstance<Phase>().map { it.id }

    @Test
    fun `saved voice guide, breath speed and breath sound survive a save and reload`() = runTest {
        val resolved = reload(
            mapOf(
                "rounds" to "2",
                "breathsPerRound" to "40",
                "breathSpeed" to "fast",
                "breathSound" to "false",
                "voiceGuide" to "false",
            ),
        )

        assertEquals("fast", resolved["breathSpeed"])
        assertEquals("false", resolved["breathSound"])
        assertEquals("false", resolved["voiceGuide"])
        val definition = entry.definition(resolved)
        assertEquals(2, definition.variables[WimHofVariables.ROUNDS])
        assertEquals(40, definition.variables[WimHofVariables.BREATHS_PER_ROUND])
        assertTrue(sfxIds(resolved).isEmpty())
        assertFalse(WimHofPhaseIds.INTRO in phaseIds(resolved))
    }

    @Test
    fun `a saved fast speed with sound on plays the fast breath clips and a saved voice guide adds the intro`() = runTest {
        val resolved = reload(mapOf("breathSpeed" to "fast", "breathSound" to "true", "voiceGuide" to "true"))

        assertTrue("whm_sfx_inhale_fast" in sfxIds(resolved))
        assertFalse("whm_sfx_inhale_normal" in sfxIds(resolved))
        assertTrue(WimHofPhaseIds.INTRO in phaseIds(resolved))
    }

    @Test
    fun `nothing saved falls back to normal speed, sound on and the voice guide on`() = runTest {
        val resolved = reload(saved = null)

        assertEquals("normal", resolved["breathSpeed"])
        assertEquals("true", resolved["breathSound"])
        assertEquals("true", resolved["voiceGuide"])
        assertTrue("whm_sfx_inhale_normal" in sfxIds(resolved))
        assertTrue(WimHofPhaseIds.INTRO in phaseIds(resolved))
    }

    @Test
    fun `missing keys in an older saved row fall back per key without touching the saved ones`() = runTest {
        val resolved = reload(mapOf("breathSpeed" to "slow"))

        assertEquals("slow", resolved["breathSpeed"])
        assertEquals("true", resolved["breathSound"])
        assertEquals("true", resolved["voiceGuide"])
        assertTrue("whm_sfx_inhale_slow" in sfxIds(resolved))
    }

    @Test
    fun `invalid stored values fall back to the defaults instead of breaking the session`() = runTest {
        val resolved = reload(
            mapOf(
                "breathsPerRound" to "35",
                "breathSpeed" to "warp",
                "breathSound" to "maybe",
                "voiceGuide" to "maybe",
            ),
        )

        val definition = entry.definition(resolved)
        assertEquals(30, definition.variables[WimHofVariables.BREATHS_PER_ROUND])
        assertTrue("whm_sfx_inhale_normal" in sfxIds(resolved))
        assertEquals("true", resolved["voiceGuide"])
        assertTrue(WimHofPhaseIds.INTRO in phaseIds(resolved))
    }

    @Test
    fun `a stored voice guide of true plays the English clips in English and is off where no clips ship`() = runTest {
        repository.saveValues(entry.id, mapOf("voiceGuide" to "true"))
        val stored = resolvedValues(entry.customizationFields, repository.getValues(entry.id))
        val english = entry.resolveSessionCustomization(stored, CatalogLocale.EN)

        assertEquals("true", english["voiceGuide"])
        assertEquals("en", english["voiceLocale"])
        assertEquals("false", com.pirxhio.affirmity.meditation.wimhof.voice.resolveVoiceGuide(stored, "fr")["voiceGuide"])
    }
}
