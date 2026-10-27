package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.local.ActivityEventEntity
import com.pirxhio.affirmity.data.local.ActivityCoverageEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityPayloadTest {
    private val event = ActivityEventEntity("account:A", "session", 20_000, "guided", 1_000,
        0, 1_000, "UTC", "breathing", "Breathing", 120, "device", "PENDING")
    @Test fun `event payload round trips bound owner without local upload fields`() {
        val data = activityEventToMap(event)
        assertEquals(false, "owner" in data)
        assertEquals(false, "uploadState" in data)
        assertEquals(event.copy(uploadState = "UPLOADED"), activityEventFromMap("account:A", "session", data))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `event document identity mismatch is rejected instead of silently changing history`() {
        activityEventFromMap("account:A", "different", activityEventToMap(event))
    }
    @Test fun `coverage payload round trips with canonical identity and destination owner`() {
        val coverage = ActivityCoverageEntity("account:A", "device", 20_000, 20_001, 1, false, "PENDING")
        val id = "5c6f3f9af527bea0f52d00e78feafb96b384f9e24b5de72afd20767811e85f69"
        assertEquals(coverage.copy(owner = "account:B", uploadState = "UPLOADED"),
            activityCoverageFromMap("account:B", id, activityCoverageToMap(coverage)))
    }
    @Test(expected = IllegalArgumentException::class)
    fun `forged coverage ID is rejected even when scalar shape is valid`() {
        val coverage = ActivityCoverageEntity("account:A", "device", 20_000, 20_001, 1, false, "PENDING")
        activityCoverageFromMap("account:A", "a".repeat(64), activityCoverageToMap(coverage))
    }
}
