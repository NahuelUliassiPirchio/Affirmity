package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.data.DefaultGroups
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.data.local.UserCollectionEntity
import com.pirxhio.affirmity.ui.groups.CATALOG_GATED_GROUP_IDS
import com.pirxhio.affirmity.ui.groups.GroupBadge
import com.pirxhio.affirmity.ui.groups.catalogUniverseGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselSourceOptionsTest {

    private val userCollections = listOf(
        UserCollectionEntity("u1", "Morning", createdAtMillis = 1, enabled = true, lastUsedAtMillis = 1),
        UserCollectionEntity("u2", "Evening", createdAtMillis = 2, enabled = false, lastUsedAtMillis = 2),
    )

    private fun options(partiallyLocked: Set<String> = CATALOG_GATED_GROUP_IDS) =
        buildSourceOptions(partiallyLocked, DefaultGroups.all, userCollections)

    @Test
    fun `offers favorites as a selectable source`() {
        assertEquals(CarouselSource.Favorites, options().favorites.source)
    }

    @Test
    fun `themes are personalizadas followed by the 14 universes`() {
        val themes = options().themes

        assertEquals(15, themes.size)
        assertEquals(CarouselSource.Theme(PERSONALIZADAS_GROUP_ID), themes.first().source)
        assertEquals(
            catalogUniverseGroups().map { CarouselSource.Theme(it.id) },
            themes.drop(1).map { it.source },
        )
    }

    @Test
    fun `every gated universe is badged partially locked for a Free user`() {
        val themes = options().themes.drop(1)

        themes.forEach { option ->
            val id = (option.source as CarouselSource.Theme).groupId
            val expected = GroupBadge.PARTIALLY_LOCKED.takeIf { id in CATALOG_GATED_GROUP_IDS }
            assertEquals(id, expected, option.badge)
        }
        assertTrue(themes.any { it.badge == GroupBadge.PARTIALLY_LOCKED })
    }

    @Test
    fun `a Pro user sees no partial lock badge on universes`() {
        assertTrue(options(partiallyLocked = emptySet()).themes.drop(1).all { it.badge == null })
    }

    @Test
    fun `personalizadas keeps its premium badge`() {
        assertEquals(GroupBadge.PREMIUM, options().themes.first().badge)
    }

    @Test
    fun `collections list the default groups first then the user collections`() {
        val collections = options().collections

        assertEquals(
            DefaultGroups.all.map { CarouselSource.Collection(it.id) } +
                listOf(CarouselSource.Collection("u1"), CarouselSource.Collection("u2")),
            collections.map { it.source },
        )
    }

    @Test
    fun `default groups use their string resource and user collections their name`() {
        val collections = options().collections

        assertEquals(SourceTitle.Res(DefaultGroups.all.first().nameRes), collections.first().title)
        assertEquals(SourceTitle.Text("Morning"), collections.first { it.source == CarouselSource.Collection("u1") }.title)
    }

    @Test
    fun `all options are selectable and unique`() {
        val all = with(options()) { listOf(favorites) + themes + collections }

        assertEquals(all.size, all.map { it.source }.toSet().size)
    }
}
