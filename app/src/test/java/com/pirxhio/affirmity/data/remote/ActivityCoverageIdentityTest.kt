package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.ActivityCoverageInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActivityCoverageIdentityTest {
    @Test fun `coverage ID follows versioned canonical UTF8 tuple and survives owner import`() {
        val interval = ActivityCoverageInterval("local:first", "device", 20_000, 20_001, 1, false)
        assertEquals("5c6f3f9af527bea0f52d00e78feafb96b384f9e24b5de72afd20767811e85f69", coverageIdentity(interval))
        assertEquals(coverageIdentity(interval), coverageIdentity(interval.copy(owner = "account:A")))
    }
    @Test fun `schema and uncertainty never collide with proven interval identity`() {
        val interval = ActivityCoverageInterval("local:first", "device", 20_000, 20_001, 1, false)
        assertNotEquals(coverageIdentity(interval), coverageIdentity(interval.copy(recordingSchema = 2)))
        assertNotEquals(coverageIdentity(interval), coverageIdentity(interval.copy(uncertain = true)))
    }
}
