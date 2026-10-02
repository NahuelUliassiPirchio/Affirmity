package com.pirxhio.affirmity.ui.affirmations

import com.pirxhio.affirmity.access.AccessTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundInterstitialPolicyTest {

    private val now = 10_000_000L

    @Test
    fun `cooldown is five minutes`() {
        assertEquals(5 * 60 * 1000L, ROUND_INTERSTITIAL_COOLDOWN_MS)
    }

    @Test
    fun `pro never sees it`() {
        assertEquals(RoundInterstitialDecision.SKIP_PREMIUM, decideRoundInterstitial(AccessTier.PRO, null, now))
        assertFalse(shouldShowRoundInterstitial(AccessTier.PRO, null, now))
    }

    @Test
    fun `free with no previous interstitial is shown`() {
        assertTrue(shouldShowRoundInterstitial(AccessTier.FREE, null, now))
    }

    @Test
    fun `cooldown boundary is exclusive at 4m59s and inclusive at 5m00s`() {
        val last = now - (ROUND_INTERSTITIAL_COOLDOWN_MS - 1_000L)
        assertEquals(RoundInterstitialDecision.SKIP_COOLDOWN, decideRoundInterstitial(AccessTier.FREE, last, now))
        val exactly = now - ROUND_INTERSTITIAL_COOLDOWN_MS
        assertEquals(RoundInterstitialDecision.SHOW, decideRoundInterstitial(AccessTier.FREE, exactly, now))
    }

    @Test
    fun `4 minutes 59 point 999 seconds still skips`() {
        val last = now - (ROUND_INTERSTITIAL_COOLDOWN_MS - 1L)
        assertEquals(RoundInterstitialDecision.SKIP_COOLDOWN, decideRoundInterstitial(AccessTier.FREE, last, now))
    }

    @Test
    fun `a last-shown timestamp in the future is treated as stale`() {
        assertTrue(shouldShowRoundInterstitial(AccessTier.FREE, now + 60_000L, now))
    }

    @Test
    fun `premium wins over cooldown`() {
        assertEquals(RoundInterstitialDecision.SKIP_PREMIUM, decideRoundInterstitial(AccessTier.PRO, now, now))
    }
}
