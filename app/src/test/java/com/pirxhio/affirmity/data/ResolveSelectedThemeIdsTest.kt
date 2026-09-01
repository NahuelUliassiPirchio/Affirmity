package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.ui.groups.catalogThemes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the pure theme-selection resolution logic extracted from `AffirmityAppState`'s init
 * collector ("Your feed" refactor §2/§4) -- theme-level equivalent of the deleted
 * `ResolveSelectedGroupIdsTest`: first-launch default, unknown-id filtering, the tier-independent
 * empty-selection fallback, and the new one-time legacy-group-to-theme migration path (scope
 * decision #4). The legacy-migration tests deliberately use REAL universe/theme ids from the
 * committed taxonomy (`ui/groups/CatalogTaxonomy.kt`) since [resolveSelectedThemeIds] calls the
 * real [catalogThemes] internally on that path; every other test uses synthetic ids to stay
 * independent of the generated catalog's exact shape. */
class ResolveSelectedThemeIdsTest {

    private val syntheticKnownIds = setOf("u1.t1", "u1.t2", "u2.t1")
    private val syntheticDefaultIds = setOf("u1.t1")

    @Test
    fun `first-ever launch with no persisted selection and no legacy data falls back to the default set`() {
        val resolved = resolveSelectedThemeIds(
            persistedThemeIds = null,
            legacyGroupIds = null,
            knownThemeIds = syntheticKnownIds,
            defaultThemeIds = syntheticDefaultIds,
        )

        assertEquals(syntheticDefaultIds, resolved)
    }

    @Test
    fun `legacy migration -- persisted null, legacy group ids present -- expands into every known theme under those universes`() {
        val allRealThemeIds = catalogThemes().map { it.id }.toSet()
        val expected = catalogThemes().filter { it.universeId == "self_worth" }.map { it.id }.toSet()
        assertTrue("fixture assumption: self_worth has at least one theme", expected.isNotEmpty())

        val resolved = resolveSelectedThemeIds(
            persistedThemeIds = null,
            legacyGroupIds = setOf("self_worth"),
            knownThemeIds = allRealThemeIds,
            defaultThemeIds = emptySet(),
        )

        assertEquals(expected, resolved)
    }

    @Test
    fun `legacy migration whose universes are all unknown or deleted falls back to the default set`() {
        val allRealThemeIds = catalogThemes().map { it.id }.toSet()

        val resolved = resolveSelectedThemeIds(
            persistedThemeIds = null,
            legacyGroupIds = setOf("some_removed_legacy_group"),
            knownThemeIds = allRealThemeIds,
            defaultThemeIds = syntheticDefaultIds,
        )

        assertEquals(syntheticDefaultIds, resolved)
    }

    @Test
    fun `once persisted is non-null, the legacy group ids are never consulted`() {
        val resolved = resolveSelectedThemeIds(
            persistedThemeIds = setOf("u1.t1"),
            legacyGroupIds = setOf("self_worth"), // would resolve to a totally different set if consulted
            knownThemeIds = syntheticKnownIds,
            defaultThemeIds = syntheticDefaultIds,
        )

        assertEquals(setOf("u1.t1"), resolved)
    }

    @Test
    fun `a persisted selection with one unknown id among otherwise-healthy ids drops only that id`() {
        val resolved = resolveSelectedThemeIds(
            persistedThemeIds = setOf("u1.t1", "some_removed_theme"),
            legacyGroupIds = null,
            knownThemeIds = syntheticKnownIds,
            defaultThemeIds = syntheticDefaultIds,
        )

        assertEquals(setOf("u1.t1"), resolved)
    }

    @Test
    fun `an explicitly empty persisted selection falls back to the default set`() {
        val resolved = resolveSelectedThemeIds(
            persistedThemeIds = emptySet(),
            legacyGroupIds = null,
            knownThemeIds = syntheticKnownIds,
            defaultThemeIds = syntheticDefaultIds,
        )

        assertEquals(syntheticDefaultIds, resolved)
    }

    @Test
    fun `a persisted selection whose every id is unknown falls back to the default set`() {
        val resolved = resolveSelectedThemeIds(
            persistedThemeIds = setOf("stale.theme.one", "stale.theme.two"),
            legacyGroupIds = null,
            knownThemeIds = syntheticKnownIds,
            defaultThemeIds = syntheticDefaultIds,
        )

        assertEquals(syntheticDefaultIds, resolved)
    }

    @Test
    fun `a healthy persisted selection with multiple surviving ids is returned verbatim`() {
        val resolved = resolveSelectedThemeIds(
            persistedThemeIds = setOf("u1.t1", "u2.t1"),
            legacyGroupIds = null,
            knownThemeIds = syntheticKnownIds,
            defaultThemeIds = syntheticDefaultIds,
        )

        assertEquals(setOf("u1.t1", "u2.t1"), resolved)
    }

    @Test
    fun `an empty draft is never valid, regardless of custom affirmations -- personalizadas never factors in`() {
        assertFalse(isDraftThemeSelectionValid(emptySet()))
    }

    @Test
    fun `a non-empty draft is valid`() {
        assertTrue(isDraftThemeSelectionValid(setOf("u1.t1")))
    }

    // --- Wiring sanity: every catalog theme resolves to a non-blank derived label -------------

    @Test
    fun `production wiring -- catalogThemes derives a non-blank label for every theme`() {
        val themes = catalogThemes()
        assertTrue("fixture assumption: the generated catalog has at least one theme", themes.isNotEmpty())
        assertTrue(themes.all { it.label.isNotBlank() })
    }
}
