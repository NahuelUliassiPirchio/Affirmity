package com.pirxhio.affirmity.ui.myaffirmations

import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockRecord
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.access.isUnlocked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [customAffirmationAccessDecision]/[customAffirmationContentKey]/
 * [isCustomAffirmationCreationLocked] (REQ-4.6/4.7, design §3.4) — the thinnest of the three access
 * facades: no per-entry lookup (unlike meditations) and no `alwaysSelected` short-circuit (unlike
 * groups), since the decision never varies by count (§0).
 */
class CustomAffirmationAccessPolicyTest {

    private val noGrants = AdUnlockState()
    private val now = 1_000_000L

    @Test
    fun `customAffirmationContentKey builds the single stable CUSTOM_AFFIRMATION_SLOT create key`() {
        assertEquals(
            ContentKey(ContentType.CUSTOM_AFFIRMATION_SLOT, "create"),
            customAffirmationContentKey(),
        )
    }

    @Test
    fun `a FREE user with no grants is LockedNeedsPro`() {
        val decision = customAffirmationAccessDecision(AccessTier.FREE, noGrants, now)
        assertEquals(AccessDecision.LockedNeedsPro, decision)
        assertTrue(isCustomAffirmationCreationLocked(decision))
        assertFalse(decision.isUnlocked)
    }

    @Test
    fun `a PRO user with no grants is Unlocked`() {
        val decision = customAffirmationAccessDecision(AccessTier.PRO, noGrants, now)
        assertEquals(AccessDecision.Unlocked, decision)
        assertFalse(isCustomAffirmationCreationLocked(decision))
        assertTrue(decision.isUnlocked)
    }

    @Test
    fun `a FREE user with a poisoned durable grant for the creation key is still LockedNeedsPro`() {
        // ContentAccess.Pro has AdUnlockPolicy.NONE, so resolveAccess must never consult grants for
        // this key regardless of tier -- proving the gate cannot be defeated by grant-state
        // tampering (REQ-4.8, mirrors AccessResolutionTest's inverted "PRO ignores grants" case).
        val key = customAffirmationContentKey()
        val poisonedGrants = AdUnlockState(
            durableUnlocks = mapOf(key to AdUnlockRecord(key = key, grantedAtMillis = now - 1_000)),
        )
        val decision = customAffirmationAccessDecision(AccessTier.FREE, poisonedGrants, now)
        assertEquals(AccessDecision.LockedNeedsPro, decision)
        assertTrue(isCustomAffirmationCreationLocked(decision))
    }

    @Test
    fun `isCustomAffirmationCreationLocked is the exact inversion of isUnlocked`() {
        assertTrue(isCustomAffirmationCreationLocked(AccessDecision.LockedNeedsPro))
        assertFalse(isCustomAffirmationCreationLocked(AccessDecision.Unlocked))
    }
}
