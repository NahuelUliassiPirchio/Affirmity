package com.pirxhio.affirmity.data

import org.junit.Assert.*
import org.junit.Test

class ActivityContinuityTest {
    @Test fun `wall clock jumps invalidate rather than certify a skipped date`() {
        val tracker = ActivityContinuity("local:G", "device", 10, 0, "UTC", 1_000)
        val result = tracker.observe(11, 30_000, "UTC", 86_401_000)
        assertTrue(result.single().uncertain)
        assertEquals(10L, result.single().fromEpochDay)
        assertEquals(11L, result.single().toEpochDay)
    }
    @Test fun `only a completely observed day is sealed and restart does not fill a gap`() {
        val tracker = ActivityContinuity("local:G", "device", 10, 0, "UTC")
        assertTrue(tracker.observe(11, 30_000, "UTC").isEmpty())
        assertEquals(listOf(ActivityCoverageInterval("local:G", "device", 11, 11, 1, false)), tracker.observe(12, 60_000, "UTC"))
        val restarted = ActivityContinuity("local:G", "device", 14, 100_000, "UTC")
        assertTrue(restarted.observe(15, 130_000, "UTC").isEmpty())
    }
    @Test fun `suspension and zone change add uncertainty rather than bridge missing observation`() {
        val tracker = ActivityContinuity("local:G", "device", 10, 0, "UTC")
        tracker.observe(11, 30_000, "UTC")
        assertTrue(tracker.observe(12, 500_000, "UTC").single().uncertain)
        assertTrue(tracker.observe(13, 530_000, "Asia/Tokyo").single().uncertain)
        assertTrue(tracker.observe(14, 560_000, "Asia/Tokyo").isEmpty())
    }
}
