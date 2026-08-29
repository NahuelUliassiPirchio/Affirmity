package com.pirxhio.affirmity.ui.affirmations

import com.pirxhio.affirmity.data.AffirmationBackground
import com.pirxhio.affirmity.ui.groups.catalogUniverseGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** RED-first (design D4): backgrounds are DERIVED, never stored -- a pure function of
 * `(groupId, id)`. */
class CatalogBackgroundsTest {

    @Test
    fun `deterministic per id across repeated calls`() {
        val first = forCatalogAffirmation("self_worth", "cat_self_worth.feeling_enough.001")
        val second = forCatalogAffirmation("self_worth", "cat_self_worth.feeling_enough.001")

        assertEquals(first, second)
    }

    @Test
    fun `ids within a universe span the palette, not a single color`() {
        val colors = (1..40)
            .map { forCatalogAffirmation("self_worth", "cat_self_worth.feeling_enough.$it") }
            .map { it.value }
            .toSet()

        assertTrue("expected more than one shade across 40 ids, got $colors", colors.size > 1)
    }

    @Test
    fun `every real universe id resolves to a color background`() {
        catalogUniverseGroups().forEach { group ->
            val background = forCatalogAffirmation(group.id, "cat_${group.id}.smoke.001")
            assertTrue(background is AffirmationBackground.Color)
        }
    }
}
