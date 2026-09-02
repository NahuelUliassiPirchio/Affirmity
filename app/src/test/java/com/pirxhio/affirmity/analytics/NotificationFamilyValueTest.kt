package com.pirxhio.affirmity.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

/** Regression test for a real bug: `fromWire` used to match by enum name (ignoreCase), which
 *  silently mapped the server's `reminder`/`reflection` wire channel tokens to UNKNOWN since their
 *  analytics display names (`AFFIRMATION`/`COMPASS`) don't literally equal the wire token. */
class NotificationFamilyValueTest {

    @Test
    fun `every real wire channel token maps to its analytics family, not UNKNOWN`() {
        assertEquals(NotificationFamilyValue.AFFIRMATION, NotificationFamilyValue.fromWire("reminder"))
        assertEquals(NotificationFamilyValue.MOOD, NotificationFamilyValue.fromWire("mood"))
        assertEquals(NotificationFamilyValue.COMPASS, NotificationFamilyValue.fromWire("reflection"))
        assertEquals(NotificationFamilyValue.STREAK, NotificationFamilyValue.fromWire("streak"))
        assertEquals(NotificationFamilyValue.HEALER, NotificationFamilyValue.fromWire("healer"))
        assertEquals(NotificationFamilyValue.MEDITATION_RETURN, NotificationFamilyValue.fromWire("meditation_return"))
    }

    @Test
    fun `missing or unrecognized wire tokens fall back to UNKNOWN`() {
        assertEquals(NotificationFamilyValue.UNKNOWN, NotificationFamilyValue.fromWire(null))
        assertEquals(NotificationFamilyValue.UNKNOWN, NotificationFamilyValue.fromWire(""))
        assertEquals(NotificationFamilyValue.UNKNOWN, NotificationFamilyValue.fromWire("not_a_real_channel"))
    }
}
