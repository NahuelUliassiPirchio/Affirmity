package com.pirxhio.affirmity.ui.affirmations

import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.Affirmation
import com.pirxhio.affirmity.data.AffirmationBackground
import com.pirxhio.affirmity.data.AffirmationSource
import com.pirxhio.affirmity.ui.groups.catalogCollections
import com.pirxhio.affirmity.ui.groups.catalogThemesById
import com.pirxhio.affirmity.ui.groups.catalogUniverseGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AffirmationOriginTest {
    private fun affirmation(
        source: AffirmationSource,
        groupId: String,
        collectionId: String?,
    ) = Affirmation(
        id = "a1",
        title = "t",
        subtitle = "",
        background = AffirmationBackground.Color("#000000"),
        groupId = groupId,
        source = source,
        collectionId = collectionId,
    )

    @Test
    fun catalogAffirmationResolvesUniverseAndThemeLabel() {
        val collection = catalogCollections().first()
        val expectedUniverse = catalogUniverseGroups().first { it.id == collection.universeId }.titleRes

        val origin = affirmationOrigin(affirmation(AffirmationSource.CATALOG, collection.universeId, collection.id))

        assertEquals(expectedUniverse, origin?.universeTitleRes)
        val theme = catalogThemesById().getValue(collection.themeId)
        assertEquals(theme.titleRes, origin?.themeTitleRes)
        assertEquals(theme.label, origin?.themeFallbackLabel)
    }

    @Test
    fun ownedAffirmationShowsOnlyThePersonalGroup() {
        val origin = affirmationOrigin(affirmation(AffirmationSource.OWNED, "personalizadas", null))

        assertEquals(R.string.affirmation_group_personalizadas_title, origin?.universeTitleRes)
        assertNull(origin?.themeTitleRes)
        assertNull(origin?.themeFallbackLabel)
    }

    @Test
    fun unknownCatalogUniverseHasNoOrigin() {
        assertNull(affirmationOrigin(affirmation(AffirmationSource.CATALOG, "nope", "nope")))
    }

    @Test
    fun knownUniverseWithUnknownCollectionKeepsUniverseOnly() {
        val universe = catalogUniverseGroups().first()

        val origin = affirmationOrigin(affirmation(AffirmationSource.CATALOG, universe.id, "gone"))

        assertEquals(universe.titleRes, origin?.universeTitleRes)
        assertNull(origin?.themeTitleRes)
        assertNull(origin?.themeFallbackLabel)
    }
}
