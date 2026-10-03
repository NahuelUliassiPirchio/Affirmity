package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.meditation.MeditationNode
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.customization.resolvedValues
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
    fun `manual release targets the exhale hold phase`() {
        assertEquals(WimHofPhaseIds.EXHALE_HOLD, entry.presentation.manualRelease?.phaseId)
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
}
