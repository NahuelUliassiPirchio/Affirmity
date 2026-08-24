package com.pirxhio.affirmity.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Full tier x policy x grant-state decision matrix for [resolveAccess] (design §3, spec §11). */
class AccessResolutionTest {

    private val key = ContentKey(ContentType.AFFIRMATION_GROUP, "fuerza_de_voluntad")

    // --- entitlement wins outright, before grants are even read ------------------------------

    @Test
    fun `PRO user is always Unlocked regardless of content requirement`() {
        val decision = resolveAccess(
            key = key,
            content = ContentAccess.Pro,
            userTier = AccessTier.PRO,
            grants = AdUnlockState(),
            nowMillis = 1_000L,
        )
        assertEquals(AccessDecision.Unlocked, decision)
    }

    @Test
    fun `PRO user is Unlocked even when grant state is poisoned with contradictory records`() {
        // A PRO user structurally cannot hold a legitimate grant (design §3 note) -- resolveAccess
        // must never consult grants for a PRO user, so even a nonsensical/poisoned grant state
        // (e.g. an expired-looking durable record for this exact key) must not affect the result.
        val poisonedGrants = AdUnlockState(
            sessionUnlocks = emptySet(),
            durableUnlocks = mapOf(key to AdUnlockRecord(key, grantedAtMillis = 0L, expiresAtMillis = -1L)),
        )
        val decision = resolveAccess(
            key = key,
            content = ContentAccess.ProOrAdTrial,
            userTier = AccessTier.PRO,
            grants = poisonedGrants,
            nowMillis = 1_000L,
        )
        assertEquals(AccessDecision.Unlocked, decision)
    }

    @Test
    fun `FREE content is always Unlocked regardless of user tier`() {
        val decision = resolveAccess(
            key = key,
            content = ContentAccess.Free,
            userTier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 1_000L,
        )
        assertEquals(AccessDecision.Unlocked, decision)
    }

    // --- EC-3: FREE content with a raw-constructed non-NONE adUnlock is inert ----------------

    @Test
    fun `EC-3 -- FREE content with adUnlock set via raw constructor is still Unlocked (inert)`() {
        // ContentAccess(FREE, adUnlock != NONE) bypasses the companions but is a documented
        // unreachable-by-convention state (spec EC-3) -- deliberately NOT given a runtime guard,
        // because the requiredTier == FREE branch short-circuits before adUnlock is consulted.
        val inertFreeWithAdUnlock = ContentAccess(AccessTier.FREE, AdUnlockPolicy.PER_USE)
        val decision = resolveAccess(
            key = key,
            content = inertFreeWithAdUnlock,
            userTier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 1_000L,
        )
        assertEquals(AccessDecision.Unlocked, decision)
    }

    // --- not entitled, adUnlock = NONE ---------------------------------------------------------

    @Test
    fun `FREE user, PRO content, no ad path yields LockedNeedsPro`() {
        val decision = resolveAccess(
            key = key,
            content = ContentAccess.Pro,
            userTier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 1_000L,
        )
        assertEquals(AccessDecision.LockedNeedsPro, decision)
    }

    @Test
    fun `policy-flipped-to-NONE -- a stale grant is ignored once the content no longer offers an ad path`() {
        // The CONTENT's current policy decides whether grants are consulted at all (design §3) --
        // if a group's adUnlock is flipped to NONE, a session/durable grant left over from before
        // the flip must stop being honored immediately.
        val staleGrants = AdUnlockState(
            sessionUnlocks = setOf(key),
            durableUnlocks = mapOf(key to AdUnlockRecord(key, grantedAtMillis = 0L)),
        )
        val decision = resolveAccess(
            key = key,
            content = ContentAccess.Pro, // adUnlock = NONE
            userTier = AccessTier.FREE,
            grants = staleGrants,
            nowMillis = 1_000L,
        )
        assertEquals(AccessDecision.LockedNeedsPro, decision)
    }

    // --- not entitled, adUnlock = PER_USE -------------------------------------------------------

    @Test
    fun `FREE user, PER_USE content, no session grant yields LockedAdUnlockable`() {
        val decision = resolveAccess(
            key = key,
            content = ContentAccess.ProOrAdPerUse,
            userTier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 1_000L,
        )
        assertEquals(AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE), decision)
    }

    @Test
    fun `FREE user, PER_USE content, key present in sessionUnlocks yields UnlockedByAd`() {
        val decision = resolveAccess(
            key = key,
            content = ContentAccess.ProOrAdPerUse,
            userTier = AccessTier.FREE,
            grants = AdUnlockState(sessionUnlocks = setOf(key)),
            nowMillis = 1_000L,
        )
        assertEquals(AccessDecision.UnlockedByAd(AdUnlockPolicy.PER_USE), decision)
    }

    @Test
    fun `FREE user, PER_USE content, a DIFFERENT key in sessionUnlocks does not unlock this one`() {
        val otherKey = ContentKey(ContentType.AFFIRMATION_GROUP, "autocuidado")
        val decision = resolveAccess(
            key = key,
            content = ContentAccess.ProOrAdPerUse,
            userTier = AccessTier.FREE,
            grants = AdUnlockState(sessionUnlocks = setOf(otherKey)),
            nowMillis = 1_000L,
        )
        assertEquals(AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE), decision)
    }

    // --- not entitled, adUnlock = ONE_TIME_TRIAL ------------------------------------------------

    @Test
    fun `FREE user, ONE_TIME_TRIAL content, no durable record yields LockedAdUnlockable`() {
        val decision = resolveAccess(
            key = key,
            content = ContentAccess.ProOrAdTrial,
            userTier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 1_000L,
        )
        assertEquals(AccessDecision.LockedAdUnlockable(AdUnlockPolicy.ONE_TIME_TRIAL), decision)
    }

    @Test
    fun `FREE user, ONE_TIME_TRIAL content, live (non-expired) durable record yields UnlockedByAd`() {
        val record = AdUnlockRecord(key, grantedAtMillis = 500L, expiresAtMillis = 10_000L)
        val decision = resolveAccess(
            key = key,
            content = ContentAccess.ProOrAdTrial,
            userTier = AccessTier.FREE,
            grants = AdUnlockState(durableUnlocks = mapOf(key to record)),
            nowMillis = 1_000L,
        )
        assertEquals(AccessDecision.UnlockedByAd(AdUnlockPolicy.ONE_TIME_TRIAL), decision)
    }

    @Test
    fun `FREE user, ONE_TIME_TRIAL content, permanent (null-expiry) durable record yields UnlockedByAd`() {
        val record = AdUnlockRecord(key, grantedAtMillis = 500L, expiresAtMillis = null)
        val decision = resolveAccess(
            key = key,
            content = ContentAccess.ProOrAdTrial,
            userTier = AccessTier.FREE,
            grants = AdUnlockState(durableUnlocks = mapOf(key to record)),
            nowMillis = Long.MAX_VALUE,
        )
        assertEquals(AccessDecision.UnlockedByAd(AdUnlockPolicy.ONE_TIME_TRIAL), decision)
    }

    @Test
    fun `spent-trial case -- expired durable record yields LockedNeedsPro, never re-offers the ad`() {
        val record = AdUnlockRecord(key, grantedAtMillis = 0L, expiresAtMillis = 5_000L)
        val decision = resolveAccess(
            key = key,
            content = ContentAccess.ProOrAdTrial,
            userTier = AccessTier.FREE,
            grants = AdUnlockState(durableUnlocks = mapOf(key to record)),
            nowMillis = 5_000L, // exactly at expiry, per hasExpired's >= boundary
        )
        assertEquals(AccessDecision.LockedNeedsPro, decision)
        assertFalse(decision.offersAdUnlock)
    }

    // --- isUnlocked / offersAdUnlock extension properties -------------------------------------

    @Test
    fun `isUnlocked is true for Unlocked and UnlockedByAd, false otherwise`() {
        assertTrue(AccessDecision.Unlocked.isUnlocked)
        assertTrue(AccessDecision.UnlockedByAd(AdUnlockPolicy.PER_USE).isUnlocked)
        assertFalse(AccessDecision.LockedNeedsPro.isUnlocked)
        assertFalse(AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE).isUnlocked)
    }

    @Test
    fun `offersAdUnlock is true only for LockedAdUnlockable`() {
        assertTrue(AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE).offersAdUnlock)
        assertFalse(AccessDecision.Unlocked.offersAdUnlock)
        assertFalse(AccessDecision.UnlockedByAd(AdUnlockPolicy.PER_USE).offersAdUnlock)
        assertFalse(AccessDecision.LockedNeedsPro.offersAdUnlock)
    }

    // --- AdUnlockRecord.hasExpired --------------------------------------------------------------

    @Test
    fun `hasExpired is false for a permanent (null-expiry) record`() {
        val record = AdUnlockRecord(key, grantedAtMillis = 0L, expiresAtMillis = null)
        assertFalse(record.hasExpired(Long.MAX_VALUE))
    }

    @Test
    fun `hasExpired is true exactly at the expiry instant`() {
        val record = AdUnlockRecord(key, grantedAtMillis = 0L, expiresAtMillis = 5_000L)
        assertTrue(record.hasExpired(5_000L))
    }

    @Test
    fun `hasExpired is false just before the expiry instant`() {
        val record = AdUnlockRecord(key, grantedAtMillis = 0L, expiresAtMillis = 5_000L)
        assertFalse(record.hasExpired(4_999L))
    }

    // --- EC-4 (documentation note, no new logic exercised beyond the purity already proven above) --
    // ONE_TIME_TRIAL grant-write racing a Pro->Free downgrade needs no special handling: grant
    // writes are create-if-absent (AdUnlockRepository contract, design §4a) and resolveAccess is a
    // pure function of its arguments with no hidden ordering dependency -- whichever of "the grant
    // write lands" or "the downgrade is observed" happens first, resolveAccess's result for a given
    // (key, content, userTier, grants, nowMillis) tuple is identical. Covered by this file's
    // purity/no-side-effect posture; no dedicated test needed beyond what's already asserted above.

    // --- ContentAccess companions sanity (used throughout this file) --------------------------

    @Test
    fun `ContentAccess companions expose the expected tier and policy combinations`() {
        assertEquals(AccessTier.FREE to AdUnlockPolicy.NONE, ContentAccess.Free.requiredTier to ContentAccess.Free.adUnlock)
        assertEquals(AccessTier.PRO to AdUnlockPolicy.NONE, ContentAccess.Pro.requiredTier to ContentAccess.Pro.adUnlock)
        assertEquals(AccessTier.PRO to AdUnlockPolicy.PER_USE, ContentAccess.ProOrAdPerUse.requiredTier to ContentAccess.ProOrAdPerUse.adUnlock)
        assertEquals(AccessTier.PRO to AdUnlockPolicy.ONE_TIME_TRIAL, ContentAccess.ProOrAdTrial.requiredTier to ContentAccess.ProOrAdTrial.adUnlock)
    }
}
