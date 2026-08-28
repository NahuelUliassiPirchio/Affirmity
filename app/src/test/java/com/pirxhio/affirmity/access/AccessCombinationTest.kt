package com.pirxhio.affirmity.access

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RED-first for design D6: the full 4x4 truth table for [mostRestrictive], plus its algebraic
 * properties (commutative, associative, [AccessDecision.Unlocked] identity).
 */
class AccessCombinationTest {

    private val unlocked = AccessDecision.Unlocked
    private val unlockedByAdPerUse = AccessDecision.UnlockedByAd(AdUnlockPolicy.PER_USE)
    private val unlockedByAdTimed = AccessDecision.UnlockedByAd(AdUnlockPolicy.TIMED_REPEATABLE)
    private val unlockedByAdTrial = AccessDecision.UnlockedByAd(AdUnlockPolicy.ONE_TIME_TRIAL)
    private val lockedNeedsPro = AccessDecision.LockedNeedsPro
    private val lockedAdPerUse = AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE)
    private val lockedAdTimed = AccessDecision.LockedAdUnlockable(AdUnlockPolicy.TIMED_REPEATABLE)
    private val lockedAdTrial = AccessDecision.LockedAdUnlockable(AdUnlockPolicy.ONE_TIME_TRIAL)

    // --- Load-bearing rows from design.md's table ---------------------------------------------

    @Test
    fun `LockedNeedsPro absorbs Unlocked`() {
        assertEquals(lockedNeedsPro, mostRestrictive(lockedNeedsPro, unlocked))
        assertEquals(lockedNeedsPro, mostRestrictive(unlocked, lockedNeedsPro))
    }

    @Test
    fun `LockedNeedsPro absorbs any LockedAdUnlockable`() {
        assertEquals(lockedNeedsPro, mostRestrictive(lockedNeedsPro, lockedAdPerUse))
        assertEquals(lockedNeedsPro, mostRestrictive(lockedAdTrial, lockedNeedsPro))
    }

    @Test
    fun `LockedNeedsPro absorbs any UnlockedByAd`() {
        assertEquals(lockedNeedsPro, mostRestrictive(lockedNeedsPro, unlockedByAdTrial))
        assertEquals(lockedNeedsPro, mostRestrictive(unlockedByAdPerUse, lockedNeedsPro))
    }

    @Test
    fun `between two LockedAdUnlockable the stricter policy wins - ONE_TIME_TRIAL beats PER_USE`() {
        assertEquals(lockedAdTrial, mostRestrictive(lockedAdPerUse, lockedAdTrial))
        assertEquals(lockedAdTrial, mostRestrictive(lockedAdTrial, lockedAdPerUse))
    }

    @Test
    fun `between two LockedAdUnlockable ONE_TIME_TRIAL beats TIMED_REPEATABLE beats PER_USE`() {
        assertEquals(lockedAdTrial, mostRestrictive(lockedAdTrial, lockedAdTimed))
        assertEquals(lockedAdTimed, mostRestrictive(lockedAdTimed, lockedAdPerUse))
    }

    @Test
    fun `a live grant does not clear the other side's locked gate`() {
        assertEquals(lockedAdPerUse, mostRestrictive(lockedAdPerUse, unlockedByAdTrial))
        assertEquals(lockedAdTimed, mostRestrictive(unlockedByAdPerUse, lockedAdTimed))
    }

    @Test
    fun `provenance survives - UnlockedByAd beats plain Unlocked`() {
        assertEquals(unlockedByAdPerUse, mostRestrictive(unlockedByAdPerUse, unlocked))
        assertEquals(unlockedByAdTrial, mostRestrictive(unlocked, unlockedByAdTrial))
    }

    @Test
    fun `between two UnlockedByAd the stricter policy wins`() {
        assertEquals(unlockedByAdTrial, mostRestrictive(unlockedByAdPerUse, unlockedByAdTrial))
        assertEquals(unlockedByAdTimed, mostRestrictive(unlockedByAdTimed, unlockedByAdPerUse))
    }

    @Test
    fun `Unlocked is the identity element`() {
        val allDecisions = listOf(
            unlocked, unlockedByAdPerUse, unlockedByAdTimed, unlockedByAdTrial,
            lockedNeedsPro, lockedAdPerUse, lockedAdTimed, lockedAdTrial,
        )
        for (d in allDecisions) {
            assertEquals(d, mostRestrictive(d, unlocked))
            assertEquals(d, mostRestrictive(unlocked, d))
        }
    }

    @Test
    fun `mostRestrictive is commutative for the full 4x4 table`() {
        val allDecisions = listOf(
            unlocked, unlockedByAdPerUse, unlockedByAdTimed, unlockedByAdTrial,
            lockedNeedsPro, lockedAdPerUse, lockedAdTimed, lockedAdTrial,
        )
        for (a in allDecisions) {
            for (b in allDecisions) {
                assertEquals("mismatch for ($a, $b)", mostRestrictive(a, b), mostRestrictive(b, a))
            }
        }
    }

    @Test
    fun `mostRestrictive is associative for the full 4x4 table`() {
        val allDecisions = listOf(
            unlocked, unlockedByAdPerUse, unlockedByAdTimed, unlockedByAdTrial,
            lockedNeedsPro, lockedAdPerUse, lockedAdTimed, lockedAdTrial,
        )
        for (a in allDecisions) {
            for (b in allDecisions) {
                for (c in allDecisions) {
                    assertEquals(
                        "mismatch for ($a, $b, $c)",
                        mostRestrictive(mostRestrictive(a, b), c),
                        mostRestrictive(a, mostRestrictive(b, c)),
                    )
                }
            }
        }
    }
}
