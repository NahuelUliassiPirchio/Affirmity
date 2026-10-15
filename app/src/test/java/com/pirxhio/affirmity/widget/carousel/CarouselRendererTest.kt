package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselRendererTest {

    private val catalog = listOf(
        catalogEntity("cat_a", text = "A text", subtitle = "A sub"),
        catalogEntity("cat_b", text = "B text"),
        catalogEntity("cat_pro", collectionId = PRO_COLLECTION, text = "Pro text"),
    )
    private val ports = FakeCarouselPorts(catalog)
    private val free = CarouselAccess.FailClosed
    private val ME = CarouselAccount.of("me")
    private val pro = CarouselAccess(AccessTier.PRO, AdUnlockState())

    private fun renderer(seed: Int = 1) =
        CarouselRenderer(ShuffleQueue(Random(seed)), CarouselFrameResolver { ports.catalogRow(it) })

    private suspend fun load(source: CarouselSource?, access: CarouselAccess = free, p: FakeCarouselPorts = ports) =
        CarouselDataLoader(p, readAccess = { access }, timeoutMillis = 4_000).load(source)

    // --- FrameResolver ---

    @Test
    fun `a catalog frame carries text and subtitle and no notice when resolved`() = runTest {
        val result = PoolResult.Resolved(listOf(CarouselRef("cat_a", UNIVERSE, FREE_COLLECTION, true)))

        val frame = CarouselFrameResolver { ports.catalogRow(it) }.resolve(result.refs.first(), result, emptyMap())

        assertEquals(CarouselFrame("A text", "A sub", null, true), frame)
    }

    @Test
    fun `a fallback result puts its reason in the frame notice`() = runTest {
        val ref = CarouselRef("cat_a", UNIVERSE, FREE_COLLECTION, true)
        val result = PoolResult.Fallback(FallbackReason.SOURCE_LOCKED, listOf(ref))

        val frame = CarouselFrameResolver { ports.catalogRow(it) }.resolve(ref, result, emptyMap())

        assertEquals(FallbackReason.SOURCE_LOCKED, frame?.notice)
    }

    @Test
    fun `a blank subtitle becomes null`() = runTest {
        val ref = CarouselRef("cat_b", UNIVERSE, FREE_COLLECTION, true)

        val frame = CarouselFrameResolver { ports.catalogRow(it) }
            .resolve(ref, PoolResult.Resolved(listOf(ref)), emptyMap())

        assertEquals("sub cat_b", frame?.subtitle)
        val blank = CarouselFrameResolver { catalogEntity("x", subtitle = "  ") }
            .resolve(ref, PoolResult.Resolved(listOf(ref)), emptyMap())
        assertNull(blank?.subtitle)
    }

    @Test
    fun `owned text is rendered through the template with overrides`() = runTest {
        val owned = ownedEntity("o1", title = "Hello [name]", subtitle = "from [place]",
            overrides = mapOf("title:0" to "Ana"))
        val ref = CarouselRef("o1", PERSONALIZADAS_GROUP_ID, null, true)

        val frame = CarouselFrameResolver { null }
            .resolve(ref, PoolResult.Resolved(listOf(ref)), mapOf("o1" to owned))

        assertEquals("Hello Ana", frame?.text)
        assertEquals("from place", frame?.subtitle)
    }

    @Test
    fun `a missing row resolves to no frame`() = runTest {
        val ref = CarouselRef("gone", UNIVERSE, FREE_COLLECTION, true)

        assertNull(CarouselFrameResolver { null }.resolve(ref, PoolResult.Resolved(listOf(ref)), emptyMap()))
    }

    // --- Renderer ---

    @Test
    fun `the first render builds the queue and shows an item`() = runTest {
        val state = CarouselState(source = CarouselSource.Theme(UNIVERSE))

        val out = renderer().render(state, advance = false, load = load(state.source), nowMillis = 0, account = ME)

        assertEquals(setOf("cat_a", "cat_b"), out.state.order.toSet())
        assertEquals(0, out.state.cursor)
        assertTrue(out.frame != null)
        assertFalse(out.state.fallback)
    }

    @Test
    fun `an advance moves to the next queued item`() = runTest {
        val state = CarouselState(
            source = CarouselSource.Theme(UNIVERSE), order = listOf("cat_a", "cat_b"), cursor = 0,
        )

        val out = renderer().render(state, advance = true, load = load(state.source), nowMillis = 0, account = ME)

        assertEquals(1, out.state.cursor)
        assertEquals("B text", out.frame?.text)
    }

    @Test
    fun `an empty catalog renders no frame without crashing`() = runTest {
        val empty = FakeCarouselPorts()
        val state = CarouselState(source = CarouselSource.Favorites)

        val out = renderer().render(state, advance = true, load = load(state.source, p = empty), nowMillis = 0, account = ME)

        assertNull(out.frame)
        assertTrue(out.state.order.isEmpty())
    }

    @Test
    fun `an empty source shows the fallback with a notice and persists the flag`() = runTest {
        val state = CarouselState(source = CarouselSource.Favorites)

        val out = renderer().render(state, advance = false, load = load(state.source), nowMillis = 0, account = ME)

        assertEquals(FallbackReason.SOURCE_EMPTY, out.frame?.notice)
        assertTrue(out.state.fallback)
        assertTrue(out.state.order.all { it in setOf("cat_a", "cat_b") })
    }

    @Test
    fun `the notice clears once the source is valid again`() = runTest {
        val withFavorite = FakeCarouselPorts(catalog, favorites = listOf("cat_a"))
        val state = CarouselState(source = CarouselSource.Favorites, order = listOf("cat_b"), fallback = true)

        val out = renderer().render(state, advance = false, load = load(state.source, p = withFavorite), nowMillis = 0, account = ME)

        assertNull(out.frame?.notice)
        assertFalse(out.state.fallback)
        assertEquals(listOf("cat_a"), out.state.order)
    }

    @Test
    fun `a Pro user sees Pro text but it is never persisted`() = runTest {
        val state = CarouselState(
            source = CarouselSource.Theme(UNIVERSE), order = listOf("cat_pro", "cat_a", "cat_b"), cursor = 0,
        )

        val out = renderer().render(state, advance = false, load = load(state.source, pro), nowMillis = 0, account = ME)

        assertEquals("Pro text", out.frame?.text)
        assertFalse(out.frame!!.isFreeTier)
        assertNull(out.state.lastText)
        assertFalse(out.state.lastFree)
    }

    @Test
    fun `lapse - after a Pro frame the Free rebuild shows free text and keeps no gated text`() = runTest {
        val proState = CarouselState(
            source = CarouselSource.Theme(UNIVERSE), order = listOf("cat_pro", "cat_a", "cat_b"), cursor = 0,
        )
        val afterPro = renderer().render(proState, advance = false, load = load(proState.source, pro), nowMillis = 0, account = ME)

        val afterLapse = renderer().render(afterPro.state, advance = false, load = load(proState.source, free), nowMillis = 0, account = ME)

        assertFalse(afterLapse.frame?.text == "Pro text")
        assertTrue(afterLapse.frame!!.isFreeTier)
        assertFalse("cat_pro" in afterLapse.state.order)
        assertTrue(afterLapse.state.lastText != "Pro text")
    }

    @Test
    fun `unavailable owned data replays the last free text without advancing`() = runTest {
        val broken = FakeCarouselPorts(catalog, ownedRead = { throw java.io.IOException("offline") })
        val state = CarouselState(
            source = CarouselSource.Theme(PERSONALIZADAS_GROUP_ID),
            order = listOf("o1", "o2"), cursor = 1,
            lastText = "Stored", lastSubtitle = "S", lastFree = true, lastAccount = ME,
        )

        val out = renderer().render(state, advance = true, load = load(state.source, p = broken), nowMillis = 0, account = ME)

        assertEquals(CarouselFrame("Stored", "S", null, true), out.frame)
        assertEquals(state.order, out.state.order)
        assertEquals(1, out.state.cursor)
    }

    @Test
    fun `unavailable owned data without a free last text falls back with a notice`() = runTest {
        val broken = FakeCarouselPorts(catalog, ownedRead = { throw java.io.IOException("offline") })
        val state = CarouselState(
            source = CarouselSource.Theme(PERSONALIZADAS_GROUP_ID),
            order = listOf("o1"), cursor = 0,
            lastText = "Gated", lastFree = false,
        )

        val out = renderer().render(state, advance = false, load = load(state.source, p = broken), nowMillis = 0, account = ME)

        assertEquals(FallbackReason.DATA_UNAVAILABLE, out.frame?.notice)
        assertTrue(out.state.fallback)
        assertTrue(out.frame!!.text in setOf("A text", "B text"))
    }

    // --- account binding ---

    private val offline = FakeCarouselPorts(catalog, ownedRead = { throw java.io.IOException("offline") })

    private fun storedFor(account: String?) = CarouselState(
        source = CarouselSource.Theme(PERSONALIZADAS_GROUP_ID),
        order = listOf("o1"), cursor = 0,
        lastText = "Private of A", lastSubtitle = "S", lastFree = true, lastAccount = account,
    )

    @Test
    fun `text stored for one account is never replayed to another account`() = runTest {
        val state = storedFor(CarouselAccount.of("A"))

        val out = renderer().render(state, advance = false, load = load(state.source, p = offline), nowMillis = 0,
            account = CarouselAccount.of("B"))

        assertFalse(out.frame?.text == "Private of A")
        assertEquals(FallbackReason.DATA_UNAVAILABLE, out.frame?.notice)
        assertFalse(out.state.lastText == "Private of A")
        assertNull(out.state.lastAccount.takeIf { out.state.lastText == null && out.state.lastFree })
    }

    @Test
    fun `text stored for an account is never replayed to a signed-out user`() = runTest {
        val state = storedFor(CarouselAccount.of("A"))

        val out = renderer().render(state, advance = false, load = load(state.source, p = offline), nowMillis = 0,
            account = CarouselAccount.SIGNED_OUT)

        assertFalse(out.frame?.text == "Private of A")
        assertFalse(out.state.lastText == "Private of A")
        assertEquals(CarouselAccount.SIGNED_OUT, out.state.lastAccount)
    }

    @Test
    fun `a mismatch replaces the stored frame with one bound to the current account`() = runTest {
        val state = storedFor(CarouselAccount.of("A")).copy(source = CarouselSource.Favorites, order = emptyList())

        val out = renderer().render(state, advance = false, load = load(state.source), nowMillis = 0,
            account = CarouselAccount.of("B"))

        assertFalse(out.state.lastText == "Private of A")
        assertEquals(CarouselAccount.of("B"), out.state.lastAccount)
    }

    @Test
    fun `text stored without an account marker is not replayed`() = runTest {
        val state = storedFor(account = null)

        val out = renderer().render(state, advance = false, load = load(state.source, p = offline), nowMillis = 0,
            account = ME)

        assertFalse(out.frame?.text == "Private of A")
    }

    @Test
    fun `the same account still replays its last free text`() = runTest {
        val state = storedFor(CarouselAccount.of("A"))

        val out = renderer().render(state, advance = false, load = load(state.source, p = offline), nowMillis = 0,
            account = CarouselAccount.of("A"))

        assertEquals(CarouselFrame("Private of A", "S", null, true), out.frame)
    }

    @Test
    fun `signed-out to signed-out still replays`() = runTest {
        val state = storedFor(CarouselAccount.SIGNED_OUT)

        val out = renderer().render(state, advance = false, load = load(state.source, p = offline), nowMillis = 0,
            account = CarouselAccount.SIGNED_OUT)

        assertEquals("Private of A", out.frame?.text)
    }

    @Test
    fun `a rendered free frame is stamped with the current account`() = runTest {
        val state = CarouselState(source = CarouselSource.Theme(UNIVERSE))

        val out = renderer().render(state, advance = false, load = load(state.source), nowMillis = 0, account = ME)

        assertEquals(ME, out.state.lastAccount)
    }
}
