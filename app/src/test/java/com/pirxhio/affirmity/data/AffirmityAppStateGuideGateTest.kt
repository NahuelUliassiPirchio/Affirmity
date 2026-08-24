package com.pirxhio.affirmity.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers tasks 1.3/1.5 (spec R1.3, design D2): the pure migration-default/backfill logic and the
 * derived "should show" resolution, table-tested over the tri-state combos, mirroring
 * [ResolveSelectedGroupIdsTest]'s style.
 */
class AffirmityAppStateGuideGateTest {

    // --- resolveGuideBackfill (design D2 migration default) ---------------------------------

    @Test
    fun `legacy install -- guideSeen null and hasCompletedOnboarding true backfills to seen`() {
        val resolved = resolveGuideBackfill(guideSeen = null, hasCompletedOnboarding = true)

        assertEquals(true, resolved)
    }

    @Test
    fun `fresh install mid-survey -- guideSeen null and hasCompletedOnboarding false stays null`() {
        val resolved = resolveGuideBackfill(guideSeen = null, hasCompletedOnboarding = false)

        assertEquals(null, resolved)
    }

    @Test
    fun `onboarding state not yet resolved -- guideSeen null and hasCompletedOnboarding null stays null`() {
        val resolved = resolveGuideBackfill(guideSeen = null, hasCompletedOnboarding = null)

        assertEquals(null, resolved)
    }

    @Test
    fun `armed guide -- guideSeen false is passed through regardless of hasCompletedOnboarding`() {
        assertEquals(false, resolveGuideBackfill(guideSeen = false, hasCompletedOnboarding = true))
        assertEquals(false, resolveGuideBackfill(guideSeen = false, hasCompletedOnboarding = null))
    }

    @Test
    fun `already-seen guide -- guideSeen true is passed through regardless of hasCompletedOnboarding`() {
        assertEquals(true, resolveGuideBackfill(guideSeen = true, hasCompletedOnboarding = true))
        assertEquals(true, resolveGuideBackfill(guideSeen = true, hasCompletedOnboarding = false))
    }

    // --- shouldShowOnboardingGuide resolution (R1.2) ------------------------------------------

    @Test
    fun `shouldShowGuide is true only when the resolved guideSeen is false`() {
        assertTrue(shouldShowGuide(resolveGuideBackfill(guideSeen = false, hasCompletedOnboarding = true)))
    }

    @Test
    fun `shouldShowGuide is false when resolved guideSeen is true, null, or backfilled to true`() {
        assertFalse(shouldShowGuide(resolveGuideBackfill(guideSeen = true, hasCompletedOnboarding = true)))
        assertFalse(shouldShowGuide(resolveGuideBackfill(guideSeen = null, hasCompletedOnboarding = false)))
        assertFalse(shouldShowGuide(resolveGuideBackfill(guideSeen = null, hasCompletedOnboarding = true)))
    }
}
