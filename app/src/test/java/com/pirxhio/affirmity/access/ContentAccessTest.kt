package com.pirxhio.affirmity.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** [ContentAccess]'s `init` invariant and the [AdUnlockPolicy.TIMED_REPEATABLE] companion
 *  (design D16): [ContentAccess.unlockWindowHours] must be non-null IFF the policy is
 *  [AdUnlockPolicy.TIMED_REPEATABLE] -- enforced at construction, not at grant time. */
class ContentAccessTest {

    @Test
    fun `TIMED_REPEATABLE with a null unlockWindowHours throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContentAccess(AccessTier.PRO, AdUnlockPolicy.TIMED_REPEATABLE, unlockWindowHours = null)
        }
    }

    @Test
    fun `any other policy with a non-null unlockWindowHours throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ContentAccess(AccessTier.PRO, AdUnlockPolicy.NONE, unlockWindowHours = 24)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContentAccess(AccessTier.PRO, AdUnlockPolicy.PER_USE, unlockWindowHours = 24)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ContentAccess(AccessTier.PRO, AdUnlockPolicy.ONE_TIME_TRIAL, unlockWindowHours = 24)
        }
    }

    @Test
    fun `ProOrAdTimed constructs with the requested window`() {
        val content = ContentAccess.ProOrAdTimed(24)

        assertEquals(AccessTier.PRO, content.requiredTier)
        assertEquals(AdUnlockPolicy.TIMED_REPEATABLE, content.adUnlock)
        assertEquals(24, content.unlockWindowHours)
    }

    @Test
    fun `TIMED_REPEATABLE with a non-null unlockWindowHours constructs fine`() {
        val content = ContentAccess(AccessTier.PRO, AdUnlockPolicy.TIMED_REPEATABLE, unlockWindowHours = 12)

        assertEquals(12, content.unlockWindowHours)
    }
}
