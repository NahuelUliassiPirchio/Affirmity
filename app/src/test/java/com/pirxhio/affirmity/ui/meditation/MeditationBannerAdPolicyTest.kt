package com.pirxhio.affirmity.ui.meditation

import com.pirxhio.affirmity.access.AccessTier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [shouldShowMeditationBanner] — the single gate deciding whether the free-tier banner
 * ad is shown on the guided meditation screen (design D-Interfaces, spec "Banner visibility
 * policy"). Pro pays to not see ads; every other tier sees it.
 */
class MeditationBannerAdPolicyTest {

    @Test
    fun `a Free-tier user sees the meditation banner`() {
        assertTrue(shouldShowMeditationBanner(AccessTier.FREE))
    }

    @Test
    fun `a Pro-tier user never sees the meditation banner`() {
        assertFalse(shouldShowMeditationBanner(AccessTier.PRO))
    }
}
