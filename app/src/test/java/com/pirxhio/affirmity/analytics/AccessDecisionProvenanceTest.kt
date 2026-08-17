package com.pirxhio.affirmity.analytics

import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AdUnlockPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/** D4/REQ-6.2: all four [AccessDecision] cases are reachable through [provenance]. */
class AccessDecisionProvenanceTest {

    @Test
    fun `Unlocked maps to UNLOCKED`() {
        assertEquals(AccessDecisionValue.UNLOCKED, AccessDecision.Unlocked.provenance())
    }

    @Test
    fun `UnlockedByAd maps to UNLOCKED_BY_AD`() {
        assertEquals(
            AccessDecisionValue.UNLOCKED_BY_AD,
            AccessDecision.UnlockedByAd(AdUnlockPolicy.PER_USE).provenance(),
        )
    }

    @Test
    fun `LockedNeedsPro maps to LOCKED_NEEDS_PRO`() {
        assertEquals(AccessDecisionValue.LOCKED_NEEDS_PRO, AccessDecision.LockedNeedsPro.provenance())
    }

    @Test
    fun `LockedAdUnlockable maps to LOCKED_AD_UNLOCKABLE`() {
        assertEquals(
            AccessDecisionValue.LOCKED_AD_UNLOCKABLE,
            AccessDecision.LockedAdUnlockable(AdUnlockPolicy.ONE_TIME_TRIAL).provenance(),
        )
    }
}
