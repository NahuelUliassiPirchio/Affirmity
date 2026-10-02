package com.pirxhio.affirmity.ui.affirmations

import com.pirxhio.affirmity.access.AccessTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundInterstitialPolicyTest {

    @Test
    fun `pro never sees it`() {
        assertEquals(RoundInterstitialDecision.SKIP_PREMIUM, decideRoundInterstitial(AccessTier.PRO))
        assertFalse(shouldShowRoundInterstitial(AccessTier.PRO))
    }

    @Test
    fun `free is always shown`() {
        assertEquals(RoundInterstitialDecision.SHOW, decideRoundInterstitial(AccessTier.FREE))
        assertTrue(shouldShowRoundInterstitial(AccessTier.FREE))
    }
}
