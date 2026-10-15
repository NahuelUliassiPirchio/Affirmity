package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockRecord
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.data.local.CatalogAffirmationRef
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.ui.groups.CatalogCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselPoolBuilderTest {

    private val now = 1_000_000L

    private val universe = "body_energy_wellbeing"
    private val freeCollection = "body_energy_wellbeing.body_acceptance.respect_my_body_today"
    private val proCollection = "body_energy_wellbeing.body_confidence.take_physical_space"
    private val timedCollection = "body_energy_wellbeing.body_confidence.comfortable_being_seen"

    private fun row(id: String, collectionId: String, groupId: String = universe) =
        CatalogAffirmationRef(id = id, groupId = groupId, collectionId = collectionId)

    private val freeRow = row("cat_free", freeCollection)
    private val proRow = row("cat_pro", proCollection)
    private val timedRow = row("cat_timed", timedCollection)

    private val free = CarouselAccess(AccessTier.FREE, AdUnlockState())
    private val pro = CarouselAccess(AccessTier.PRO, AdUnlockState())

    private fun fallbackCatalog() = listOf(
        row("cat_free", freeCollection),
        row("cat_pro", proCollection),
        row("cat_timed", timedCollection),
        row("cat_other_free", freeCollection),
    )

    private fun build(
        source: CarouselSource?,
        snapshot: CarouselSnapshot,
        access: CarouselAccess = free,
        collectionsById: Map<String, CatalogCollection>? = null,
    ): PoolResult = if (collectionsById == null) {
        CarouselPoolBuilder.build(source, snapshot, access, now)
    } else {
        CarouselPoolBuilder.build(source, snapshot, access, now, collectionsById = collectionsById)
    }

    private fun ids(result: PoolResult) = result.refs.map { it.id }

    private fun timedGrantFor(collectionId: String, expiresAt: Long): AdUnlockState {
        val key = ContentKey(ContentType.AFFIRMATION_COLLECTION, collectionId)
        return AdUnlockState(timedUnlocks = mapOf(key to AdUnlockRecord(key, now - 10, expiresAt)))
    }

    // --- gating ---

    @Test
    fun `theme source with a Pro collection resolves fully for a Pro user`() {
        val snapshot = CarouselSnapshot(catalogRefs = listOf(freeRow, proRow), fallbackRefs = fallbackCatalog())

        val result = build(CarouselSource.Theme(universe), snapshot, pro)

        assertTrue(result is PoolResult.Resolved)
        assertEquals(listOf("cat_free", "cat_pro"), ids(result))
    }

    @Test
    fun `theme source excludes gated rows for a Free user without falling back`() {
        val snapshot = CarouselSnapshot(catalogRefs = listOf(freeRow, proRow), fallbackRefs = fallbackCatalog())

        val result = build(CarouselSource.Theme(universe), snapshot, free)

        assertTrue(result is PoolResult.Resolved)
        assertEquals(listOf("cat_free"), ids(result))
    }

    @Test
    fun `source whose rows are all gated falls back with the locked reason`() {
        val snapshot = CarouselSnapshot(catalogRefs = listOf(proRow), fallbackRefs = fallbackCatalog())

        val result = build(CarouselSource.Theme(universe), snapshot, free)

        assertEquals(FallbackReason.SOURCE_LOCKED, (result as PoolResult.Fallback).reason)
        assertEquals(listOf("cat_free", "cat_other_free"), ids(result))
    }

    @Test
    fun `an active ad unlock grant allows the collection`() {
        val snapshot = CarouselSnapshot(catalogRefs = listOf(timedRow), fallbackRefs = fallbackCatalog())
        val access = CarouselAccess(AccessTier.FREE, timedGrantFor(timedCollection, expiresAt = now + 1000))

        val result = build(CarouselSource.Theme(universe), snapshot, access)

        assertTrue(result is PoolResult.Resolved)
        assertEquals(listOf("cat_timed"), ids(result))
    }

    @Test
    fun `an expired ad unlock grant no longer allows the collection`() {
        val snapshot = CarouselSnapshot(catalogRefs = listOf(timedRow), fallbackRefs = fallbackCatalog())
        val access = CarouselAccess(AccessTier.FREE, timedGrantFor(timedCollection, expiresAt = now - 1))

        val result = build(CarouselSource.Theme(universe), snapshot, access)

        assertEquals(FallbackReason.SOURCE_LOCKED, (result as PoolResult.Fallback).reason)
    }

    @Test
    fun `fallback never contains a gated row even when the user holds a grant`() {
        val snapshot = CarouselSnapshot(catalogRefs = listOf(proRow), fallbackRefs = fallbackCatalog())
        val access = CarouselAccess(AccessTier.FREE, timedGrantFor(timedCollection, expiresAt = now + 1000))

        val result = build(CarouselSource.Theme(universe), snapshot, access)

        assertTrue(result is PoolResult.Fallback)
        assertFalse("cat_timed" in ids(result))
        assertTrue(result.refs.all { it.isFreeTier })
    }

    @Test
    fun `fail-closed access of Free and no grants never yields gated items`() {
        val snapshot = CarouselSnapshot(catalogRefs = listOf(proRow, timedRow), fallbackRefs = fallbackCatalog())

        val result = build(CarouselSource.Theme(universe), snapshot, CarouselAccess.FailClosed)

        assertTrue(result is PoolResult.Fallback)
        assertTrue(result.refs.all { it.isFreeTier })
    }

    @Test
    fun `PER_USE collection resolves to the fallback because session unlocks never count`() {
        val perUse = CatalogCollection(
            id = "c.per_use",
            universeId = universe,
            themeId = "$universe.t",
            access = ContentAccess.ProOrAdPerUse,
            order = 1,
        )
        val snapshot = CarouselSnapshot(
            catalogRefs = listOf(row("cat_per_use", perUse.id)),
            fallbackRefs = fallbackCatalog(),
        )

        val result = build(CarouselSource.Theme(universe), snapshot, free, mapOf(perUse.id to perUse))

        assertEquals(FallbackReason.SOURCE_LOCKED, (result as PoolResult.Fallback).reason)
    }

    // --- isFreeTier ---

    @Test
    fun `isFreeTier is false for a Pro collection even when access is Pro`() {
        val snapshot = CarouselSnapshot(catalogRefs = listOf(freeRow, proRow), fallbackRefs = fallbackCatalog())

        val result = build(CarouselSource.Theme(universe), snapshot, pro)

        assertEquals(mapOf("cat_free" to true, "cat_pro" to false), result.refs.associate { it.id to it.isFreeTier })
    }

    @Test
    fun `isFreeTier is false for a gated collection even with an active grant`() {
        val snapshot = CarouselSnapshot(catalogRefs = listOf(timedRow), fallbackRefs = fallbackCatalog())
        val access = CarouselAccess(AccessTier.FREE, timedGrantFor(timedCollection, expiresAt = now + 1000))

        val result = build(CarouselSource.Theme(universe), snapshot, access)

        assertEquals(listOf(false), result.refs.map { it.isFreeTier })
    }

    @Test
    fun `owned rows are free tier`() {
        val snapshot = CarouselSnapshot(owned = OwnedRead.Available(listOf("o1")), fallbackRefs = fallbackCatalog())

        val result = build(CarouselSource.Theme(PERSONALIZADAS_GROUP_ID), snapshot, free)

        assertEquals(listOf(CarouselRef("o1", PERSONALIZADAS_GROUP_ID, null, true)), result.refs)
    }

    // --- owned affirmations ---

    @Test
    fun `personalizadas resolves from the owned ids`() {
        val snapshot = CarouselSnapshot(owned = OwnedRead.Available(listOf("o1", "o2")), fallbackRefs = fallbackCatalog())

        val result = build(CarouselSource.Theme(PERSONALIZADAS_GROUP_ID), snapshot, free)

        assertTrue(result is PoolResult.Resolved)
        assertEquals(listOf("o1", "o2"), ids(result))
    }

    @Test
    fun `personalizadas with no owned affirmations falls back as empty`() {
        val snapshot = CarouselSnapshot(owned = OwnedRead.Available(emptyList()), fallbackRefs = fallbackCatalog())

        val result = build(CarouselSource.Theme(PERSONALIZADAS_GROUP_ID), snapshot, free)

        assertEquals(FallbackReason.SOURCE_EMPTY, (result as PoolResult.Fallback).reason)
    }

    @Test
    fun `personalizadas with unavailable owned data falls back as data unavailable`() {
        val snapshot = CarouselSnapshot(owned = OwnedRead.Unavailable, fallbackRefs = fallbackCatalog())

        val result = build(CarouselSource.Theme(PERSONALIZADAS_GROUP_ID), snapshot, free)

        assertEquals(FallbackReason.DATA_UNAVAILABLE, (result as PoolResult.Fallback).reason)
        assertTrue(result.refs.all { it.isFreeTier })
    }

    @Test
    fun `favorites mix catalog and owned ids and skip unknown ids`() {
        val snapshot = CarouselSnapshot(
            favoriteIds = listOf("cat_free", "o1", "ghost"),
            owned = OwnedRead.Available(listOf("o1")),
            catalogRefs = listOf(freeRow),
            fallbackRefs = fallbackCatalog(),
        )

        val result = build(CarouselSource.Favorites, snapshot, free)

        assertTrue(result is PoolResult.Resolved)
        assertEquals(listOf("cat_free", "o1"), ids(result))
    }

    @Test
    fun `favorites that reference only unavailable owned data fall back as data unavailable`() {
        val snapshot = CarouselSnapshot(
            favoriteIds = listOf("o1"),
            owned = OwnedRead.Unavailable,
            fallbackRefs = fallbackCatalog(),
        )

        val result = build(CarouselSource.Favorites, snapshot, free)

        assertEquals(FallbackReason.DATA_UNAVAILABLE, (result as PoolResult.Fallback).reason)
    }

    // --- empty, deleted or locked sources ---

    @Test
    fun `favorites with no favorites fall back as empty`() {
        val snapshot = CarouselSnapshot(fallbackRefs = fallbackCatalog())

        val result = build(CarouselSource.Favorites, snapshot, free)

        assertEquals(FallbackReason.SOURCE_EMPTY, (result as PoolResult.Fallback).reason)
        assertEquals(listOf("cat_free", "cat_other_free"), ids(result))
    }

    @Test
    fun `a deleted user collection falls back as missing`() {
        val snapshot = CarouselSnapshot(collections = emptyMap(), fallbackRefs = fallbackCatalog())

        val result = build(CarouselSource.Collection("gone"), snapshot, free)

        assertEquals(FallbackReason.SOURCE_MISSING, (result as PoolResult.Fallback).reason)
    }

    @Test
    fun `an unknown theme falls back as missing`() {
        val snapshot = CarouselSnapshot(fallbackRefs = fallbackCatalog())

        val result = build(CarouselSource.Theme("not_a_theme"), snapshot, free)

        assertEquals(FallbackReason.SOURCE_MISSING, (result as PoolResult.Fallback).reason)
    }

    @Test
    fun `an empty collection falls back as empty`() {
        val snapshot = CarouselSnapshot(collections = mapOf("c1" to emptyList()), fallbackRefs = fallbackCatalog())

        val result = build(CarouselSource.Collection("c1"), snapshot, free)

        assertEquals(FallbackReason.SOURCE_EMPTY, (result as PoolResult.Fallback).reason)
    }

    @Test
    fun `a collection of only gated members falls back as locked`() {
        val snapshot = CarouselSnapshot(
            collections = mapOf("c1" to listOf("cat_pro")),
            catalogRefs = listOf(proRow),
            fallbackRefs = fallbackCatalog(),
        )

        val result = build(CarouselSource.Collection("c1"), snapshot, free)

        assertEquals(FallbackReason.SOURCE_LOCKED, (result as PoolResult.Fallback).reason)
    }

    @Test
    fun `a collection resolves its members when allowed`() {
        val snapshot = CarouselSnapshot(
            collections = mapOf("c1" to listOf("cat_pro", "cat_free")),
            catalogRefs = listOf(proRow, freeRow),
            fallbackRefs = fallbackCatalog(),
        )

        val result = build(CarouselSource.Collection("c1"), snapshot, pro)

        assertTrue(result is PoolResult.Resolved)
        assertEquals(listOf("cat_pro", "cat_free"), ids(result))
    }

    @Test
    fun `a fallback source that becomes valid again resolves without a notice`() {
        val snapshot = CarouselSnapshot(
            favoriteIds = listOf("cat_free"),
            catalogRefs = listOf(freeRow),
            fallbackRefs = fallbackCatalog(),
        )

        assertTrue(build(CarouselSource.Favorites, snapshot, free) is PoolResult.Resolved)
    }

    @Test
    fun `no source yields a fallback without a notice`() {
        val result = build(null, CarouselSnapshot(fallbackRefs = fallbackCatalog()), free)

        assertEquals(null, (result as PoolResult.Fallback).reason)
        assertEquals(listOf("cat_free", "cat_other_free"), ids(result))
    }

    @Test
    fun `an empty catalog yields an empty fallback`() {
        val result = build(CarouselSource.Favorites, CarouselSnapshot(), free)

        assertTrue(result is PoolResult.Fallback)
        assertTrue(result.refs.isEmpty())
    }
}
