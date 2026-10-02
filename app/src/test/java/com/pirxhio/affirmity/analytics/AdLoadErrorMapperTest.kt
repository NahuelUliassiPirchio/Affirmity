package com.pirxhio.affirmity.analytics

import org.junit.Assert.assertEquals
import org.junit.Test

class AdLoadErrorMapperTest {
    @Test
    fun `AdMob load error codes map to bounded reasons`() {
        assertEquals(AdFailureReason.NO_FILL, adLoadErrorCodeToReason(3))
        assertEquals(AdFailureReason.NETWORK, adLoadErrorCodeToReason(2))
        assertEquals(AdFailureReason.CONFIG, adLoadErrorCodeToReason(1))
        assertEquals(AdFailureReason.CONFIG, adLoadErrorCodeToReason(8))
        assertEquals(AdFailureReason.UNKNOWN, adLoadErrorCodeToReason(0))
        assertEquals(AdFailureReason.UNKNOWN, adLoadErrorCodeToReason(999))
    }
}
