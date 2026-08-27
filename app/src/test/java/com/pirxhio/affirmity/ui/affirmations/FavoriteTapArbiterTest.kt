package com.pirxhio.affirmity.ui.affirmations

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteTapArbiterTest {
    @Test
    fun `single tap waits for the double tap window`() {
        val arbiter = FavoriteTapArbiter()

        assertEquals(
            TokenTapDecision.Wait("k", FavoriteTapArbiter.DEFAULT_DOUBLE_TAP_WINDOW_MILLIS),
            arbiter.onTokenTap("k", nowMillis = 1_000L),
        )
    }

    @Test
    fun `second tap on the same key inside the window toggles favorite`() {
        val arbiter = FavoriteTapArbiter()

        assertEquals(TokenTapDecision.Wait("k", 300L), arbiter.onTokenTap("k", 1_000L))
        assertEquals(TokenTapDecision.ToggleFavorite, arbiter.onTokenTap("k", 1_299L))
    }

    @Test
    fun `second tap exactly at the boundary starts a new window`() {
        val arbiter = FavoriteTapArbiter()

        assertEquals(TokenTapDecision.Wait("k", 300L), arbiter.onTokenTap("k", 1_000L))
        assertEquals(TokenTapDecision.Wait("k", 300L), arbiter.onTokenTap("k", 1_300L))
    }

    @Test
    fun `tap on a different key starts that key's window`() {
        val arbiter = FavoriteTapArbiter()

        assertEquals(TokenTapDecision.Wait("k1", 300L), arbiter.onTokenTap("k1", 1_000L))
        assertEquals(TokenTapDecision.Wait("k2", 300L), arbiter.onTokenTap("k2", 1_100L))
    }

    @Test
    fun `third tap after a double tap starts a fresh window`() {
        val arbiter = FavoriteTapArbiter()

        assertEquals(TokenTapDecision.Wait("k", 300L), arbiter.onTokenTap("k", 1_000L))
        assertEquals(TokenTapDecision.ToggleFavorite, arbiter.onTokenTap("k", 1_100L))
        assertEquals(TokenTapDecision.Wait("k", 300L), arbiter.onTokenTap("k", 1_200L))
    }

    @Test
    fun `reset makes the next tap a first tap`() {
        val arbiter = FavoriteTapArbiter()
        arbiter.onTokenTap("k", 1_000L)

        arbiter.reset()

        assertEquals(TokenTapDecision.Wait("k", 300L), arbiter.onTokenTap("k", 1_100L))
    }

    @Test
    fun `second pointer down inside window toggles even when its click callback arrives late`() {
        val coordinator = FavoriteTokenTapCoordinator()

        coordinator.onPointerDown("k", 1_000L)
        assertEquals(
            TokenTapDecision.Wait("k", 300L),
            coordinator.onTokenClick("k", callbackAtMillis = 1_050L),
        )

        coordinator.onPointerDown("k", 1_299L)
        assertEquals(
            TokenTapDecision.ToggleFavorite,
            coordinator.onTokenClick("k", callbackAtMillis = 1_450L),
        )
    }

    @Test
    fun `disabled favorite arbitration starts editing immediately on every token click`() {
        val coordinator = FavoriteTokenTapCoordinator(favoriteTapEnabled = false)

        assertEquals(false, coordinator.onPointerDown("k", 1_000L))
        assertEquals(
            TokenTapDecision.StartEditing("k"),
            coordinator.onTokenClick("k", callbackAtMillis = 1_050L),
        )
        assertEquals(false, coordinator.onPointerDown("k", 1_100L))
        assertEquals(
            TokenTapDecision.StartEditing("k"),
            coordinator.onTokenClick("k", callbackAtMillis = 1_150L),
        )
    }
}
