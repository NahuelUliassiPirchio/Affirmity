package com.pirxhio.affirmity

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.meditation.SessionEndReason
import com.pirxhio.affirmity.ui.meditation.catalog.findMeditationCatalogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runtime coverage for the three [AffirmityApp] guided-meditation-route helpers extracted for
 * testability (see `MainActivity.kt`) — closes the verify-report CRITICAL gaps that static
 * inspection alone had covered.
 */
class MainActivityTest {

    private val now = 1_000_000L

    // --- resolveSelectedMeditationEntry (REQ-5.4.3, AC15): unknown restored id fail-safe --------

    @Test
    fun `resolveSelectedMeditationEntry returns null for an unknown restored id instead of throwing`() {
        val resolved = resolveSelectedMeditationEntry("this-id-does-not-exist-in-the-catalog")

        assertNull(resolved)
    }

    @Test
    fun `resolveSelectedMeditationEntry returns null when no id is selected`() {
        assertNull(resolveSelectedMeditationEntry(null))
    }

    @Test
    fun `resolveSelectedMeditationEntry resolves a known id to its catalog entry`() {
        val entry = requireNotNull(findMeditationCatalogEntry("calma"))

        assertEquals(entry, resolveSelectedMeditationEntry("calma"))
    }

    // --- isMeditationLaunchBlocked (REQ-5.4.1, AC14): launch-time access re-check ----------------

    @Test
    fun `isMeditationLaunchBlocked is false for a Free entry regardless of tier`() {
        val freeEntry = requireNotNull(findMeditationCatalogEntry("calma"))

        assertFalse(isMeditationLaunchBlocked(freeEntry, AccessTier.FREE, AdUnlockState(), now))
    }

    @Test
    fun `isMeditationLaunchBlocked blocks a Pro entry when re-resolved with a downgraded tier`() {
        val proEntry = requireNotNull(findMeditationCatalogEntry("body_scan"))

        // Simulates EC-5: a live Pro -> Free transition (or a session swap that cleared
        // sessionAdUnlocks) between the Discover tap and this screen's composition. The function
        // re-resolves access from the CURRENT tier/grants passed in -- never a cached decision --
        // so a revoked entitlement is caught here rather than reusing the tap-time grant.
        assertTrue(isMeditationLaunchBlocked(proEntry, AccessTier.FREE, AdUnlockState(), now))
    }

    @Test
    fun `isMeditationLaunchBlocked does not block a Pro entry when the current tier is still Pro`() {
        val proEntry = requireNotNull(findMeditationCatalogEntry("body_scan"))

        assertFalse(isMeditationLaunchBlocked(proEntry, AccessTier.PRO, AdUnlockState(), now))
    }

    // --- handleGuidedMeditationSessionEnded (REQ-5.6, AC6): guided completion records streak -----

    @Test
    fun `handleGuidedMeditationSessionEnded records the streak on Completed`() {
        var recordedStreak = false
        val consumedCalls = mutableListOf<Pair<String, SessionEndReason>>()

        handleGuidedMeditationSessionEnded(
            entryId = "calma",
            reason = SessionEndReason.Completed,
            consumePlaybackUnlock = { id, reason -> consumedCalls.add(id to reason) },
            recordMeditationCompleted = { recordedStreak = true },
        )

        assertTrue(recordedStreak)
        assertEquals(listOf("calma" to SessionEndReason.Completed), consumedCalls)
    }

    @Test
    fun `handleGuidedMeditationSessionEnded does not record the streak on Cancelled`() {
        var recordedStreak = false
        val consumedCalls = mutableListOf<Pair<String, SessionEndReason>>()

        handleGuidedMeditationSessionEnded(
            entryId = "calma",
            reason = SessionEndReason.Cancelled,
            consumePlaybackUnlock = { id, reason -> consumedCalls.add(id to reason) },
            recordMeditationCompleted = { recordedStreak = true },
        )

        // consumeMeditationPlaybackUnlock still runs for every terminal reason (an ad watched and
        // then abandoned mid-session is still a use) -- only the streak call is Completed-gated.
        assertFalse(recordedStreak)
        assertEquals(listOf("calma" to SessionEndReason.Cancelled), consumedCalls)
    }
}
