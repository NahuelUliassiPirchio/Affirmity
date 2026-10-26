package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.meditation.BreathRole
import com.pirxhio.affirmity.meditation.MeditationNode
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.breathing478.breathing478MeditationDefinition
import com.pirxhio.affirmity.meditation.nadishodhana.NadiShodhanaConfig
import com.pirxhio.affirmity.meditation.nadishodhana.nadiShodhanaMeditationDefinition
import com.pirxhio.affirmity.meditation.om.omMeditationDefinition
import com.pirxhio.affirmity.meditation.wimhof.WimHofPhaseIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathRoleDerivationTest {

    /** Every phase under a Repeat whose child is the `*breath` sequence `breathingBlock` emits
     * (callers may prefix its id, e.g. `calm_breath`). */
    private fun countedBreathPhaseIds(node: MeditationNode): List<String> = when (node) {
        is MeditationSequence -> node.children.flatMap(::countedBreathPhaseIds)
        is Repeat ->
            if ((node.child as? MeditationSequence)?.id?.endsWith("breath") == true) {
                collectPhases(node.child).map { it.id }
            } else {
                countedBreathPhaseIds(node.child)
            }
        else -> emptyList()
    }

    @Test
    fun `across the whole catalog every counted breath phase has a role`() {
        var blocks = 0
        meditationCatalog().forEach { entry ->
            val definition = entry.definition(emptyMap())
            val roles = breathRolesById(definition)
            val counted = countedBreathPhaseIds(definition.root)
            if (counted.isNotEmpty()) blocks++
            counted.forEach { id -> assertTrue("${entry.id}: '$id' has no role", id in roles) }
        }
        assertTrue("expected many breathingBlock entries, saw $blocks", blocks >= 16)
    }

    @Test
    fun `no role-less phase id collides with a role id inside one definition`() {
        meditationCatalog().forEach { entry ->
            val definition = entry.definition(emptyMap())
            val roles = breathRolesById(definition)
            collectPhases(definition.root).filter { it.breathRole == null }.forEach {
                assertNull("${entry.id}: '${it.id}' collides with a role'd id", roles[it.id])
            }
        }
    }

    @Test
    fun `no phase id maps to two different roles inside one definition`() {
        meditationCatalog().forEach { entry ->
            val definition = entry.definition(emptyMap())
            collectPhases(definition.root).filter { it.breathRole != null }.groupBy { it.id }.forEach { (id, phases) ->
                assertEquals("${entry.id}: '$id'", 1, phases.map { it.breathRole }.distinct().size)
            }
        }
    }

    @Test
    fun `wim hof holds and recovery stay on the ring, its breathing phases carry roles`() {
        val definition = meditationCatalog().first { it.id == "wim_hof" }.definition(emptyMap())
        val roles = breathRolesById(definition)

        assertEquals(mapOf("inhale" to BreathRole.INHALE, "exhale" to BreathRole.EXHALE), roles)
        assertNull(roles[WimHofPhaseIds.EXHALE_HOLD])
        assertNull(roles[WimHofPhaseIds.RECOVERY_HOLD])
    }

    @Test
    fun `nadi shodhana maps inhales, exhales and holds`() {
        val roles = breathRolesById(nadiShodhanaMeditationDefinition(NadiShodhanaConfig(retention = true)))

        assertEquals(
            mapOf(
                "inhale_left" to BreathRole.INHALE,
                "hold_left" to BreathRole.HOLD_FULL,
                "exhale_right" to BreathRole.EXHALE,
                "inhale_right" to BreathRole.INHALE,
                "hold_right" to BreathRole.HOLD_FULL,
                "exhale_left" to BreathRole.EXHALE,
            ),
            roles,
        )
    }

    @Test
    fun `om inhales then chants as the exhale`() {
        assertEquals(
            mapOf("inhale" to BreathRole.INHALE, "om" to BreathRole.EXHALE),
            breathRolesById(omMeditationDefinition()),
        )
    }

    @Test
    fun `rapid breath meditations keep the ring`() {
        listOf("kapalabhati", "breath_of_fire").forEach { id ->
            val entry = meditationCatalog().first { it.id == id }
            assertTrue("$id", breathRolesById(entry.definition(emptyMap())).isEmpty())
        }
    }

    @Test
    fun `box breathing holds are full after the inhale and empty after the exhale`() {
        val roles = breathRolesById(meditationCatalog().first { it.id == "box_breathing" }.definition(emptyMap()))

        assertEquals(BreathRole.INHALE, roles["inhale"])
        assertEquals(BreathRole.HOLD_FULL, roles["hold_in"])
        assertEquals(BreathRole.EXHALE, roles["exhale"])
        assertEquals(BreathRole.HOLD_EMPTY, roles["hold_out"])
    }

    @Test
    fun `4-7-8 holds after the inhale and stays full`() {
        val roles = breathRolesById(breathing478MeditationDefinition())

        assertEquals(BreathRole.HOLD_FULL, roles["hold_in"])
        assertNull(roles["hold_out"])
    }
}
