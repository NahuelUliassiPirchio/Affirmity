package com.pirxhio.affirmity.widget.carousel

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselStateCodecTest {

    private fun roundTrip(state: CarouselState): CarouselState {
        val prefs = mutablePreferencesOf()
        CarouselStateCodec.write(prefs, state)
        return CarouselStateCodec.read(prefs)
    }

    private val full = CarouselState(
        source = CarouselSource.Theme("self_worth"),
        order = listOf("cat_a", "cat_b", "cat_c"),
        cursor = 2,
        advancePending = true,
        fallback = true,
        lastText = "I am enough",
        lastSubtitle = "Always",
        lastFree = true,
    )

    // --- S4.1 ---

    @Test
    fun `encode then decode is the identity`() {
        assertEquals(full, roundTrip(full))
    }

    @Test
    fun `every source kind round trips`() {
        listOf(
            CarouselSource.Favorites,
            CarouselSource.Theme("personalizadas"),
            CarouselSource.Collection("default.morning_start"),
            CarouselSource.Collection("user.with:colon"),
            null,
        ).forEach { source ->
            assertEquals(source, roundTrip(full.copy(source = source)).source)
        }
    }

    @Test
    fun `an empty prefs store decodes to the empty state`() {
        assertEquals(CarouselState(), CarouselStateCodec.read(mutablePreferencesOf()))
    }

    @Test
    fun `a state without a subtitle round trips`() {
        assertEquals(full.copy(lastSubtitle = null), roundTrip(full.copy(lastSubtitle = null)))
    }

    @Test
    fun `an empty order round trips as empty`() {
        assertEquals(emptyList<String>(), roundTrip(full.copy(order = emptyList())).order)
    }

    @Test
    fun `an unknown stored source decodes to null`() {
        val prefs = mutablePreferencesOf()
        CarouselStateCodec.write(prefs, full)
        prefs[CarouselStateCodec.SOURCE] = "garbage"

        assertNull(CarouselStateCodec.read(prefs).source)
    }

    @Test
    fun `order is capped at 400 ids when encoded`() {
        val big = full.copy(order = (1..500).map { "id$it" }, cursor = 0)

        assertEquals(400, roundTrip(big).order.size)
    }

    // --- S4.2 ---

    @Test
    fun `instances are independent`() {
        val a = mutablePreferencesOf()
        val b = mutablePreferencesOf()
        CarouselStateCodec.write(a, full)
        CarouselStateCodec.write(b, full.copy(source = CarouselSource.Favorites, order = listOf("x"), cursor = 0))

        val before = CarouselStateCodec.read(b)
        CarouselStateCodec.write(a, full.copy(cursor = 0))

        assertEquals(before, CarouselStateCodec.read(b))
    }

    // --- persistedLast (D3b, S5.5) ---

    private fun frame(isFree: Boolean) =
        CarouselFrame(text = "Pro text", subtitle = "sub", notice = null, isFreeTier = isFree)

    @Test
    fun `persistedLast is null for a non-free frame`() {
        assertNull(persistedLast(frame(isFree = false)))
    }

    @Test
    fun `persistedLast keeps text and subtitle for a free frame`() {
        assertEquals(LastShown("Pro text", "sub"), persistedLast(frame(isFree = true)))
    }

    @Test
    fun `showing a non-free frame clears previously stored last text`() {
        val stored = full.withShown(frame(isFree = false))

        assertNull(stored.lastText)
        assertNull(stored.lastSubtitle)
        assertFalse(stored.lastFree)
    }

    @Test
    fun `showing a free frame stores the text and marks it free`() {
        val stored = CarouselState().withShown(frame(isFree = true))

        assertEquals("Pro text", stored.lastText)
        assertEquals("sub", stored.lastSubtitle)
        assertTrue(stored.lastFree)
    }

    @Test
    fun `clearing a non-free frame removes the keys from the encoded prefs`() {
        val prefs = mutablePreferencesOf()
        CarouselStateCodec.write(prefs, full)
        CarouselStateCodec.write(prefs, full.withShown(frame(isFree = false)))

        assertFalse(prefs.contains(CarouselStateCodec.LAST_TEXT))
        assertFalse(prefs.contains(CarouselStateCodec.LAST_SUBTITLE))
        assertEquals(false, prefs[CarouselStateCodec.LAST_FREE])
    }

    @Test
    fun `lapse - a Pro frame persisted then rebuilt under Free leaves no gated text in state`() {
        // Pro user sees a gated item: nothing textual is persisted.
        val afterPro = CarouselState(order = listOf("cat_pro", "cat_free"), cursor = 0)
            .withShown(frame(isFree = false))
        // The user lapses; the rebuild now resolves a free frame.
        val afterLapse = afterPro.withShown(CarouselFrame("Free text", null, FallbackReason.SOURCE_LOCKED, true))

        assertNull(afterPro.lastText)
        assertEquals("Free text", afterLapse.lastText)
        val prefs = mutablePreferencesOf()
        CarouselStateCodec.write(prefs, afterPro)
        assertTrue(prefs.asMap().values.none { it.toString().contains("Pro text") })
    }

    @Test
    fun `order may contain Pro ids but the encoded state never contains gated text`() {
        val state = CarouselState(order = listOf("cat_pro_id"), cursor = 0).withShown(frame(isFree = false))
        val prefs = mutablePreferencesOf()
        CarouselStateCodec.write(prefs, state)

        assertTrue(prefs[CarouselStateCodec.ORDER]!!.contains("cat_pro_id"))
        assertTrue(prefs.asMap().values.none { it.toString().contains("Pro text") })
    }

    // --- replay (S6.3, S6.4) ---

    @Test
    fun `replay returns the stored text only when it was free`() {
        assertEquals(LastShown("I am enough", "Always"), full.replayableLast())
    }

    @Test
    fun `replay returns nothing when the stored frame was not free`() {
        assertNull(full.copy(lastFree = false).replayableLast())
    }

    @Test
    fun `replay returns nothing when no text was stored`() {
        assertNull(full.copy(lastText = null).replayableLast())
    }

    @Test
    fun `a stored flag without text or text without flag never replays after decoding`() {
        val prefs: Preferences = mutablePreferencesOf().also {
            CarouselStateCodec.write(it, full.copy(lastFree = false))
        }

        assertNull(CarouselStateCodec.read(prefs).replayableLast())
    }
}
