package com.pirxhio.affirmity.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InterstitialLoadThrottleTest {

    private val now = 1_000_000L

    @Test
    fun `a first ever load is never throttled`() {
        assertFalse(isInterstitialLoadThrottled(lastAttemptAt = null, now = now, afterDismiss = false))
    }

    @Test
    fun `a retry inside the window is throttled so a no-fill unit never loops`() {
        val last = now - (INTERSTITIAL_LOAD_THROTTLE_MS - 1L)
        assertTrue(isInterstitialLoadThrottled(lastAttemptAt = last, now = now, afterDismiss = false))
    }

    @Test
    fun `a retry at the window boundary is allowed`() {
        val last = now - INTERSTITIAL_LOAD_THROTTLE_MS
        assertFalse(isInterstitialLoadThrottled(lastAttemptAt = last, now = now, afterDismiss = false))
    }

    /** A dismiss proves the unit just filled, so the next round's ad must not wait out the window. */
    @Test
    fun `a reload right after a dismiss bypasses the window`() {
        val last = now - 1_000L
        assertFalse(isInterstitialLoadThrottled(lastAttemptAt = last, now = now, afterDismiss = true))
    }
}
