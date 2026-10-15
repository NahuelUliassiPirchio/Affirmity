package com.pirxhio.affirmity.widget.carousel

import androidx.compose.ui.graphics.Color
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.DefaultGroups
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.data.local.UserCollectionEntity
import com.pirxhio.affirmity.ui.groups.catalogUniverseGroups
import com.pirxhio.affirmity.ui.theme.GroupHighlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CarouselThemeTest {

    private val userCollections = listOf(
        UserCollectionEntity("u1", "Morning", 1, true, 1, highlightId = "rose"),
        UserCollectionEntity("u2", "Plain", 2, true, 2),
    )

    private fun theme(source: CarouselSource?, fallback: Boolean = false) =
        carouselTheme(source, fallback, userCollections = userCollections)

    @Test
    fun `favorites are titled and use the default highlight`() {
        assertEquals(
            CarouselTheme(SourceTitle.Res(R.string.your_feed_source_favorites), GroupHighlight.Default.color),
            theme(CarouselSource.Favorites),
        )
    }

    @Test
    fun `a catalog theme takes its title and universe accent`() {
        val group = catalogUniverseGroups().first()
        val result = theme(CarouselSource.Theme(group.id))

        assertEquals(SourceTitle.Res(group.titleRes), result.title)
        assertNotEquals(GroupHighlight.Default.color, result.highlight)
    }

    @Test
    fun `every catalog universe resolves to a distinct non-default accent`() {
        val accents = catalogUniverseGroups().map { theme(CarouselSource.Theme(it.id)).highlight }

        assertEquals(accents.size, accents.toSet().size)
    }

    @Test
    fun `personalizadas is titled but keeps the default highlight`() {
        val result = theme(CarouselSource.Theme(PERSONALIZADAS_GROUP_ID))

        assertEquals(GroupHighlight.Default.color, result.highlight)
        assertEquals(true, result.title != null)
    }

    @Test
    fun `an unknown theme degrades to the untitled default`() {
        assertEquals(CarouselTheme.Default, theme(CarouselSource.Theme("nope")))
    }

    @Test
    fun `a default collection uses its name and highlight`() {
        val group = DefaultGroups.all.first()
        val result = theme(CarouselSource.Collection(group.id))

        assertEquals(SourceTitle.Res(group.nameRes), result.title)
        assertEquals(GroupHighlight.fromId(group.highlightId).color, result.highlight)
    }

    @Test
    fun `a user collection uses its name and highlight`() {
        assertEquals(
            CarouselTheme(SourceTitle.Text("Morning"), GroupHighlight.Rose.color),
            theme(CarouselSource.Collection("u1")),
        )
        assertEquals(GroupHighlight.Default.color, theme(CarouselSource.Collection("u2")).highlight)
    }

    @Test
    fun `an unknown collection degrades to the untitled default`() {
        assertEquals(CarouselTheme.Default, theme(CarouselSource.Collection("gone")))
    }

    @Test
    fun `the fallback and an unset source use the untitled default`() {
        assertEquals(CarouselTheme.Default, theme(CarouselSource.Favorites, fallback = true))
        assertEquals(CarouselTheme.Default, theme(null))
        assertEquals(Color(0xFF5AB3C8), CarouselTheme.Default.highlight)
    }
}
