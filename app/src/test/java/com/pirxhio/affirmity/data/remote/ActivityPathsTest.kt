package com.pirxhio.affirmity.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityPathsTest {
    @Test fun `journal and coverage paths remain UID bound`() {
        assertEquals("users/A/activityEvents/event", FirestorePaths.activityEventDoc("A", "event"))
        assertEquals("users/B/activityEvents/event", FirestorePaths.activityEventDoc("B", "event"))
        assertEquals("users/A/activityCoverage/interval", FirestorePaths.activityCoverageDoc("A", "interval"))
        assertEquals("users/A/meta/activity-v1", FirestorePaths.activityMigratedMarkerDoc("A"))
    }
}
