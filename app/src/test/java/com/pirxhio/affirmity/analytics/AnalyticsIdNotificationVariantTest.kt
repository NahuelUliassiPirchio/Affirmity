package com.pirxhio.affirmity.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

/** REQ (Notifications V2 design §9): [AnalyticsId.ofNotificationVariant] is the only way a
 *  server-authored, unbounded `variant_key` string reaches the [AnalyticsEvent] boundary --
 *  anything outside its declared shape must map to `"unknown"`, never pass through raw. */
class AnalyticsIdNotificationVariantTest {

    @Test
    fun `a well-formed catalog key passes through unchanged`() {
        assertEquals(
            "streak_risk_14plus_a",
            AnalyticsId.ofNotificationVariant("streak_risk_14plus_a").value,
        )
    }

    @Test
    fun `a single-character key is accepted`() {
        assertEquals("a", AnalyticsId.ofNotificationVariant("a").value)
    }

    @Test
    fun `uppercase characters fall back to unknown`() {
        assertEquals("unknown", AnalyticsId.ofNotificationVariant("Streak_Risk").value)
    }

    @Test
    fun `whitespace falls back to unknown`() {
        assertEquals("unknown", AnalyticsId.ofNotificationVariant("streak risk").value)
    }

    @Test
    fun `punctuation falls back to unknown`() {
        assertEquals("unknown", AnalyticsId.ofNotificationVariant("streak-risk!").value)
    }

    @Test
    fun `empty string falls back to unknown`() {
        assertEquals("unknown", AnalyticsId.ofNotificationVariant("").value)
    }

    @Test
    fun `a 40-character key is the accepted boundary`() {
        val key = "a".repeat(40)
        assertEquals(key, AnalyticsId.ofNotificationVariant(key).value)
    }

    @Test
    fun `a 41-character key falls back to unknown`() {
        val key = "a".repeat(41)
        assertEquals("unknown", AnalyticsId.ofNotificationVariant(key).value)
    }
}
