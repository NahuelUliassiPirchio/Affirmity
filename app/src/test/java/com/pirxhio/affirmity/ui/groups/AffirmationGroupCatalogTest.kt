package com.pirxhio.affirmity.ui.groups

import com.pirxhio.affirmity.access.isUnlocked
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.data.resolveSelectedThemeIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers design D17's legacy-group removal: `bienestar`/`autocuidado`/`fuerza_de_voluntad` are
 * DELETED outright from the selector, with no alias, migration, or fallback beyond D18's
 * tier-independent minimum-selection invariant. The theme-level legacy MIGRATION path (scope
 * decision #4 of the "Your feed" refactor) is covered separately in `ResolveSelectedThemeIdsTest`. */
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
    fun `a legacy selection referencing only a deleted legacy group has nothing to migrate and lands on a valid default`() {
        val knownThemeIds = catalogThemes().map { it.id }.toSet()
        val defaultThemeIds = catalogThemes()
            .filter {
                themeAccessDecision(
                    it.id,
                    com.pirxhio.affirmity.access.AccessTier.FREE,
                    com.pirxhio.affirmity.access.AdUnlockState(),
                    System.currentTimeMillis(),
                ).isUnlocked
            }
            .map { it.id }.toSet()

        val resolved = resolveSelectedThemeIds(
            persistedThemeIds = null,
            legacyGroupIds = setOf("bienestar"),
            knownThemeIds = knownThemeIds,
            defaultThemeIds = defaultThemeIds,
        )

        assertTrue(
            "must land on a valid, non-empty default selection -- personalizadas is not a theme " +
                "and is never a member of the resolved set",
            resolved.isNotEmpty() && PERSONALIZADAS_GROUP_ID !in resolved,
        )
        assertTrue(resolved.all { it in knownThemeIds })
        assertEquals(defaultThemeIds, resolved)
    }
}
