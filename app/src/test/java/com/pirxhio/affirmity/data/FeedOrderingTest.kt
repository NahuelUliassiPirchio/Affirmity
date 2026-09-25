package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.FeedSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the pure feed-ordering logic backing the "Random order" toggle (design "Ordering
 * algorithm" decision) and the extended dirty check (spec "Extended isDirty Detection"). Both
 * functions are pure -- no DataStore, no Compose state -- so this file is plain JUnit. */
class FeedOrderingTest {

    private data class Item(val id: String, val source: String)

    private val ownAndFavoritesAndCatalog = listOf(
        Item("own.1", "own"),
        Item("own.2", "own"),
        Item("fav.1", "favorites"),
        Item("fav.2", "favorites"),
        Item("cat.1", "catalog"),
        Item("cat.2", "catalog"),
        Item("cat.3", "catalog"),
        Item("cat.4", "catalog"),
    )

    // --- orderFeed --------------------------------------------------------------------------

    @Test
    fun `orderFeed with randomizeOrder off returns the input in its original order`() {
        val sources = FeedSources(randomizeOrder = false, orderSeed = 999L)
        val result = orderFeed(ownAndFavoritesAndCatalog, sources, idOf = Item::id)

        assertEquals(ownAndFavoritesAndCatalog, result)
    }

    @Test
    fun `orderFeed with the same seed and items produces the same order every time`() {
        val sources = FeedSources(randomizeOrder = true, orderSeed = 42L)

        val first = orderFeed(ownAndFavoritesAndCatalog, sources, idOf = Item::id)
        val second = orderFeed(ownAndFavoritesAndCatalog, sources, idOf = Item::id)

        assertEquals(first, second)
    }

    @Test
    fun `orderFeed with a different seed produces a different order`() {
        val seedA = FeedSources(randomizeOrder = true, orderSeed = 1L)
        val seedB = FeedSources(randomizeOrder = true, orderSeed = 2L)

        val resultA = orderFeed(ownAndFavoritesAndCatalog, seedA, idOf = Item::id)
        val resultB = orderFeed(ownAndFavoritesAndCatalog, seedB, idOf = Item::id)

        assertNotEquals(resultA, resultB)
    }

    @Test
    fun `orderFeed keeps the relative order of remaining rows after one row is removed`() {
        val sources = FeedSources(randomizeOrder = true, orderSeed = 7L)

        val fullOrder = orderFeed(ownAndFavoritesAndCatalog, sources, idOf = Item::id)
        val withoutOne = ownAndFavoritesAndCatalog.filterNot { it.id == "cat.2" }
        val reducedOrder = orderFeed(withoutOne, sources, idOf = Item::id)

        val expectedRelativeOrder = fullOrder.filterNot { it.id == "cat.2" }
        assertEquals(expectedRelativeOrder, reducedOrder)
    }

    @Test
    fun `orderFeed interleaves mixed segments instead of grouping by source`() {
        val sources = FeedSources(randomizeOrder = true, orderSeed = 7L)

        val result = orderFeed(ownAndFavoritesAndCatalog, sources, idOf = Item::id)
        val resultSources = result.map { it.source }
        val groupedBySource = ownAndFavoritesAndCatalog.map { it.source }

        assertNotEquals(
            "seed 7 happens to reproduce the input's grouped-by-source order -- pick a different fixture seed",
            groupedBySource,
            resultSources,
        )
    }

    // --- isFeedDraftDirty --------------------------------------------------------------------

    @Test
    fun `isFeedDraftDirty is false when draft matches committed themes and sources`() {
        val committed = FeedSources(includeFavorites = true, includeOwn = false, randomizeOrder = true, orderSeed = 5L)
        val draft = committed

        assertEquals(
            false,
            isFeedDraftDirty(
                draftThemes = setOf("u1.t1"),
                committedThemes = setOf("u1.t1"),
                draft = draft,
                committed = committed,
            ),
        )
    }

    @Test
    fun `isFeedDraftDirty is true when only randomizeOrder differs`() {
        val committed = FeedSources(randomizeOrder = false)
        val draft = committed.copy(randomizeOrder = true)

        assertTrue(
            isFeedDraftDirty(
                draftThemes = setOf("u1.t1"),
                committedThemes = setOf("u1.t1"),
                draft = draft,
                committed = committed,
            ),
        )
    }

    @Test
    fun `isFeedDraftDirty is true when includeFavorites differs`() {
        val committed = FeedSources(includeFavorites = false)
        val draft = committed.copy(includeFavorites = true)

        assertTrue(
            isFeedDraftDirty(
                draftThemes = setOf("u1.t1"),
                committedThemes = setOf("u1.t1"),
                draft = draft,
                committed = committed,
            ),
        )
    }

    @Test
    fun `isFeedDraftDirty is true when includeOwn differs`() {
        val committed = FeedSources(includeOwn = true)
        val draft = committed.copy(includeOwn = false)

        assertTrue(
            isFeedDraftDirty(
                draftThemes = setOf("u1.t1"),
                committedThemes = setOf("u1.t1"),
                draft = draft,
                committed = committed,
            ),
        )
    }

    @Test
    fun `isFeedDraftDirty ignores orderSeed differences`() {
        val committed = FeedSources(randomizeOrder = true, orderSeed = 1L)
        val draft = committed.copy(orderSeed = 999L)

        assertEquals(
            false,
            isFeedDraftDirty(
                draftThemes = setOf("u1.t1"),
                committedThemes = setOf("u1.t1"),
                draft = draft,
                committed = committed,
            ),
        )
    }

    @Test
    fun `isFeedDraftDirty is true when theme sets differ`() {
        val sources = FeedSources()

        assertTrue(
            isFeedDraftDirty(
                draftThemes = setOf("u1.t1", "u1.t2"),
                committedThemes = setOf("u1.t1"),
                draft = sources,
                committed = sources,
            ),
        )
    }
}
