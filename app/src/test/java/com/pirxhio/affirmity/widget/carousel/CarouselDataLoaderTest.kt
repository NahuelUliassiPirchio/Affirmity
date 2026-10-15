package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselDataLoaderTest {

    private val catalog = listOf(
        catalogEntity("cat_a"),
        catalogEntity("cat_b"),
        catalogEntity("cat_pro", collectionId = PRO_COLLECTION),
    )
    private val owned = listOf(ownedEntity("o1"), ownedEntity("o2"))

    private fun loader(ports: CarouselDataPorts, access: CarouselAccess = CarouselAccess.FailClosed) =
        CarouselDataLoader(ports, readAccess = { access }, timeoutMillis = 4_000)

    @Test
    fun `the access reader result is passed through`() = runTest {
        val access = CarouselAccess(AccessTier.PRO, CarouselAccess.FailClosed.grants)

        val load = loader(FakeCarouselPorts(catalog), access).load(CarouselSource.Theme(UNIVERSE))

        assertEquals(access, load.access)
    }

    @Test
    fun `a catalog theme loads its group rows and skips the owned read`() = runTest {
        val ports = FakeCarouselPorts(catalog, owned)

        val load = loader(ports).load(CarouselSource.Theme(UNIVERSE))

        assertEquals(listOf(UNIVERSE), ports.groupReads)
        assertEquals(0, ports.ownedReads)
        assertEquals(setOf("cat_a", "cat_b", "cat_pro"), load.snapshot.catalogRefs.map { it.id }.toSet())
        assertEquals(3, load.snapshot.fallbackRefs.size)
    }

    @Test
    fun `personalizadas loads the owned affirmations from the data source`() = runTest {
        val ports = FakeCarouselPorts(catalog, owned)

        val load = loader(ports).load(CarouselSource.Theme(PERSONALIZADAS_GROUP_ID))

        assertEquals(OwnedRead.Available(listOf("o1", "o2")), load.snapshot.owned)
        assertEquals(setOf("o1", "o2"), load.ownedById.keys)
    }

    @Test
    fun `favorites load ids, owned rows and the catalog refs of the members only`() = runTest {
        val ports = FakeCarouselPorts(catalog, owned, favorites = listOf("cat_b", "o2", "ghost"))

        val load = loader(ports).load(CarouselSource.Favorites)

        assertEquals(listOf("cat_b", "o2", "ghost"), load.snapshot.favoriteIds)
        assertEquals(listOf("cat_b"), load.snapshot.catalogRefs.map { it.id })
        assertEquals(OwnedRead.Available(listOf("o1", "o2")), load.snapshot.owned)
    }

    @Test
    fun `a collection loads the member map and member refs`() = runTest {
        val ports = FakeCarouselPorts(catalog, owned, collections = mapOf("c1" to listOf("cat_a", "o1")))

        val load = loader(ports).load(CarouselSource.Collection("c1"))

        assertEquals(mapOf("c1" to listOf("cat_a", "o1")), load.snapshot.collections)
        assertEquals(listOf("cat_a"), load.snapshot.catalogRefs.map { it.id })
    }

    @Test
    fun `no source loads only the fallback catalog`() = runTest {
        val ports = FakeCarouselPorts(catalog, owned)

        val load = loader(ports).load(null)

        assertEquals(0, ports.ownedReads)
        assertTrue(load.snapshot.catalogRefs.isEmpty())
        assertEquals(3, load.snapshot.fallbackRefs.size)
    }

    @Test
    fun `an owned read failure makes owned data unavailable`() = runTest {
        val ports = FakeCarouselPorts(catalog, ownedRead = { throw IOException("offline") })

        val load = loader(ports).load(CarouselSource.Theme(PERSONALIZADAS_GROUP_ID))

        assertEquals(OwnedRead.Unavailable, load.snapshot.owned)
        assertTrue(load.ownedById.isEmpty())
    }

    @Test
    fun `an owned read timeout makes owned data unavailable`() = runTest {
        val ports = FakeCarouselPorts(catalog, ownedRead = { awaitCancellation() })

        val load = loader(ports).load(CarouselSource.Theme(PERSONALIZADAS_GROUP_ID))

        assertEquals(OwnedRead.Unavailable, load.snapshot.owned)
        assertEquals(4_000L, testScheduler.currentTime)
    }
}
