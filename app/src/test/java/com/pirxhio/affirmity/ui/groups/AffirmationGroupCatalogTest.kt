package com.pirxhio.affirmity.ui.groups

import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.data.resolveSelectedGroupIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers design D17's legacy-group removal: `bienestar`/`autocuidado`/`fuerza_de_voluntad` are
 * DELETED outright from the selector, with no alias, migration, or fallback beyond D18's
 * tier-independent minimum-selection invariant (covered separately in `ResolveSelectedGroupIdsTest`). */
class AffirmationGroupCatalogTest {

    private val legacyGroupIds = setOf("bienestar", "autocuidado", "fuerza_de_voluntad")

    @Test
    fun `selectableAffirmationGroups has 15 entries -- personalizadas plus the 14 catalog universes`() {
        assertEquals(15, selectableAffirmationGroups().size)
    }

    @Test
    fun `selectableAffirmationGroups contains personalizadas`() {
        assertTrue(selectableAffirmationGroups().any { it.id == PERSONALIZADAS_GROUP_ID })
    }

    @Test
    fun `selectableAffirmationGroups contains none of the 3 deleted legacy groups`() {
        val ids = selectableAffirmationGroups().map { it.id }.toSet()
        assertFalse(ids.any { it in legacyGroupIds })
    }

    @Test
    fun `a persisted selection referencing only a deleted legacy id drops it and lands on a valid default`() {
        val knownIds = selectableAffirmationGroups().map { it.id }.toSet()
        val defaultThematicIds = defaultAffirmationGroups()
            .filter { it.isThematic && it.access.requiredTier == com.pirxhio.affirmity.access.AccessTier.FREE }
            .map { it.id }.toSet()

        val resolved = resolveSelectedGroupIds(
            persisted = setOf("bienestar"),
            knownIds = knownIds,
            defaultThematicIds = defaultThematicIds,
        )

        assertFalse("bienestar" in resolved)
        assertTrue(PERSONALIZADAS_GROUP_ID in resolved)
        assertTrue("must land on a valid, non-empty default selection", resolved.any { it != PERSONALIZADAS_GROUP_ID })
        assertTrue(resolved.all { it in knownIds })
    }
}
