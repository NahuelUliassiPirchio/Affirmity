package com.pirxhio.affirmity.widget.carousel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselRenderMergeTest {

    private val unconfigured = CarouselState(source = null, order = listOf("cat_a"), cursor = 0)
    private val rendered = unconfigured.copy(order = listOf("cat_a", "cat_b"), cursor = 1, lastText = "A")

    @Test
    fun `a render stores its queue and frame when nothing changed meanwhile`() {
        assertEquals(rendered, mergeRendered(current = unconfigured, basedOn = unconfigured, rendered = rendered))
    }

    @Test
    fun `an advance raised while rendering is kept`() {
        val merged = mergeRendered(unconfigured.copy(advancePending = true), unconfigured, rendered)

        assertTrue(merged.advancePending)
    }

    @Test
    fun `a render that started before the source was configured never restores the old source`() {
        val configured = CarouselState(source = CarouselSource.Theme("self_worth"))

        val merged = mergeRendered(current = configured, basedOn = unconfigured, rendered = rendered)

        assertEquals(configured, merged)
    }

    @Test
    fun `a render based on a different source is discarded even when it had one`() {
        val basedOn = unconfigured.copy(source = CarouselSource.Favorites)
        val configured = CarouselState(source = CarouselSource.Collection("default.morning_start"))

        val merged = mergeRendered(current = configured, basedOn = basedOn, rendered = rendered.copy(source = CarouselSource.Favorites))

        assertEquals(configured, merged)
    }

    @Test
    fun `a refresh requested while rendering is kept so the live session renders again`() {
        val merged = mergeRendered(unconfigured.copy(refreshNonce = 3), unconfigured.copy(refreshNonce = 2), rendered.copy(refreshNonce = 2))

        assertEquals(3L, merged.refreshNonce)
    }

    @Test
    fun `configuring a source starts a fresh queue and requests a refresh`() {
        val stored = rendered.copy(refreshNonce = 4, advancePending = true)

        val configured = stored.reconfigured(CarouselSource.Favorites)

        assertEquals(CarouselState(source = CarouselSource.Favorites, refreshNonce = 5), configured)
    }

    @Test
    fun `a live session reloads only for a refresh newer than the frame it shows`() {
        assertTrue(needsReload(requestedNonce = 3, renderedNonce = 2))
        assertFalse(needsReload(requestedNonce = 2, renderedNonce = 2))
        assertFalse(needsReload(requestedNonce = 1, renderedNonce = 2))
    }
}
