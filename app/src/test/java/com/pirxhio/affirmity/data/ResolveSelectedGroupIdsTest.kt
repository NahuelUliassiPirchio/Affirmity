package com.pirxhio.affirmity.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the pure selection-resolution logic extracted from `AffirmityAppState`'s init collector
 * (design §4/§6): first-launch default, unknown-id filtering, and personalizadas force-inclusion. */
class ResolveSelectedGroupIdsTest {

    private val knownIds = setOf("personalizadas", "bienestar", "autocuidado", "fuerza_de_voluntad")
    private val defaultThematicIds = setOf("bienestar")

    @Test
    fun `first-ever launch with no persisted selection falls back to the default thematic ids`() {
        val resolved = resolveSelectedGroupIds(
            persisted = null,
            knownIds = knownIds,
            defaultThematicIds = defaultThematicIds,
        )

        assertEquals(setOf("bienestar", "personalizadas"), resolved)
    }

    @Test
    fun `a persisted selection with an unknown id drops that id`() {
        val resolved = resolveSelectedGroupIds(
            persisted = setOf("bienestar", "some_removed_group"),
            knownIds = knownIds,
            defaultThematicIds = defaultThematicIds,
        )

        assertEquals(setOf("bienestar", "personalizadas"), resolved)
    }

    @Test
    fun `personalizadas is force-included even if absent from the persisted selection`() {
        val resolved = resolveSelectedGroupIds(
            persisted = setOf("autocuidado"),
            knownIds = knownIds,
            defaultThematicIds = defaultThematicIds,
        )

        assertEquals(setOf("autocuidado", "personalizadas"), resolved)
    }

    @Test
    fun `an explicitly empty persisted selection is respected, not treated as first-launch`() {
        val resolved = resolveSelectedGroupIds(
            persisted = emptySet(),
            knownIds = knownIds,
            defaultThematicIds = defaultThematicIds,
        )

        assertEquals(setOf("personalizadas"), resolved)
    }
}
