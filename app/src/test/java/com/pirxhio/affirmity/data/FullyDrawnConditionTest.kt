package com.pirxhio.affirmity.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FullyDrawnConditionTest {

    // --- isFeedContentReady ----------------------------------------------------------------

    @Test
    fun `feed is ready as soon as any catalog or owned row exists`() {
        assertTrue(isFeedContentReady(hasCatalogRows = true, hasOwnedRows = false,
            catalogEmitted = true, ownedEmitted = false, catalogSeedSettled = false))
        assertTrue(isFeedContentReady(hasCatalogRows = false, hasOwnedRows = true,
            catalogEmitted = false, ownedEmitted = true, catalogSeedSettled = false))
    }

    @Test
    fun `feed is not ready while nothing has emitted`() {
        assertFalse(isFeedContentReady(hasCatalogRows = false, hasOwnedRows = false,
            catalogEmitted = false, ownedEmitted = false, catalogSeedSettled = true))
    }

    @Test
    fun `an empty catalog emission does not count while the seed is still running`() {
        assertFalse(isFeedContentReady(hasCatalogRows = false, hasOwnedRows = false,
            catalogEmitted = true, ownedEmitted = true, catalogSeedSettled = false))
    }

    @Test
    fun `feed is genuinely resolved-empty once both sources emitted and the seed settled`() {
        assertTrue(isFeedContentReady(hasCatalogRows = false, hasOwnedRows = false,
            catalogEmitted = true, ownedEmitted = true, catalogSeedSettled = true))
    }

    @Test
    fun `resolved-empty needs both sources to have emitted`() {
        assertFalse(isFeedContentReady(hasCatalogRows = false, hasOwnedRows = false,
            catalogEmitted = true, ownedEmitted = false, catalogSeedSettled = true))
        assertFalse(isFeedContentReady(hasCatalogRows = false, hasOwnedRows = false,
            catalogEmitted = false, ownedEmitted = true, catalogSeedSettled = true))
    }

    // --- isHomeFullyDrawn -------------------------------------------------------------------

    @Test
    fun `nothing is reported before the splash gate resolves`() {
        assertFalse(isHomeFullyDrawn(initialContentResolved = false, hasCompletedOnboarding = true,
            feedContentReady = true, streakResolved = true))
        assertFalse(isHomeFullyDrawn(initialContentResolved = false, hasCompletedOnboarding = false,
            feedContentReady = true, streakResolved = true))
    }

    @Test
    fun `onboarding is reported as soon as the gate resolves even with no feed or streak data`() {
        assertTrue(isHomeFullyDrawn(initialContentResolved = true, hasCompletedOnboarding = false,
            feedContentReady = false, streakResolved = false))
    }

    @Test
    fun `home needs the feed content and the first streak emission`() {
        assertFalse(isHomeFullyDrawn(initialContentResolved = true, hasCompletedOnboarding = true,
            feedContentReady = false, streakResolved = true))
        assertFalse(isHomeFullyDrawn(initialContentResolved = true, hasCompletedOnboarding = true,
            feedContentReady = true, streakResolved = false))
        assertTrue(isHomeFullyDrawn(initialContentResolved = true, hasCompletedOnboarding = true,
            feedContentReady = true, streakResolved = true))
    }
}
