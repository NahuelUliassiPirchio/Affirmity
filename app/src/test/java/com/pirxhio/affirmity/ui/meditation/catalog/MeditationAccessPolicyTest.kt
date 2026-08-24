package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockPolicy
import com.pirxhio.affirmity.access.AdUnlockRecord
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.access.isUnlocked
import com.pirxhio.affirmity.ui.groups.GroupBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [meditationAccessDecision]/[meditationContentKey]/[isMeditationLocked]/
 * [canWatchAdToUnlockMeditation]/[deriveMeditationBadge] (REQ-4.7, design §3.4) across the full
 * tier x policy x grant-state matrix, over the real 7 catalog entries (T2.10 — retargeted from
 * PR1's hand-built fixture entries now that `meditationCatalog()` exists, spec §11).
 *
 * One representative real entry per [com.pirxhio.affirmity.access.ContentAccess] shape: `calma`
 * (Free), `body_scan` (Pro), `enfoque` (ProOrAdPerUse), `dormir` (ProOrAdTrial) — matching REQ-4.6.
 */
class MeditationAccessPolicyTest {

    private fun entry(id: String) = requireNotNull(findMeditationCatalogEntry(id)) { "unknown id: $id" }

    private val freeEntry = entry("calma")
    private val proEntry = entry("body_scan")
    private val perUseEntry = entry("enfoque")
    private val trialEntry = entry("dormir")

    private val noGrants = AdUnlockState()
    private val now = 1_000_000L

    private fun decisionFor(entry: MeditationCatalogEntry, tier: AccessTier, grants: AdUnlockState = noGrants) =
        meditationAccessDecision(entry, tier, grants, now)

    // --- meditationContentKey: the ONE construction site ----------------------------------------

    @Test
    fun `meditationContentKey builds a MEDITATION-typed ContentKey from the entry id`() {
        assertEquals(ContentKey(ContentType.MEDITATION, freeEntry.id), meditationContentKey(freeEntry.id))
    }

    // --- Free entry: unlocked at both tiers, never locked, never badged -------------------------

    @Test
    fun `a Free entry is unlocked and unbadged for a Free user`() {
        val decision = decisionFor(freeEntry, AccessTier.FREE)
        assertEquals(AccessDecision.Unlocked, decision)
        assertTrue(decision.isUnlocked)
        assertFalse(isMeditationLocked(decision))
        assertFalse(canWatchAdToUnlockMeditation(decision))
        assertNull(deriveMeditationBadge(freeEntry, decision))
    }

    @Test
    fun `a Free entry is unlocked and unbadged for a Pro user`() {
        val decision = decisionFor(freeEntry, AccessTier.PRO)
        assertEquals(AccessDecision.Unlocked, decision)
        assertFalse(isMeditationLocked(decision))
        assertNull(deriveMeditationBadge(freeEntry, decision))
    }

    // --- Pro entry (no ad path): locked+Premium-badged for Free, unlocked+unbadged for Pro ------

    @Test
    fun `a Pro-only entry is locked and shows the Premium badge for a Free user`() {
        val decision = decisionFor(proEntry, AccessTier.FREE)
        assertEquals(AccessDecision.LockedNeedsPro, decision)
        assertTrue(isMeditationLocked(decision))
        assertFalse(canWatchAdToUnlockMeditation(decision))
        assertEquals(GroupBadge.PREMIUM, deriveMeditationBadge(proEntry, decision))
    }

    @Test
    fun `a Pro-only entry is unlocked and unbadged for a Pro user`() {
        val decision = decisionFor(proEntry, AccessTier.PRO)
        assertEquals(AccessDecision.Unlocked, decision)
        assertFalse(isMeditationLocked(decision))
        assertNull(deriveMeditationBadge(proEntry, decision))
    }

    // --- ProOrAdPerUse entry: FREE-no-grant / FREE-with-grant / PRO ------------------------------

    @Test
    fun `a PER_USE entry is locked but ad-unlockable, and AD_UNLOCK-badged, for a Free user with no grant`() {
        val decision = decisionFor(perUseEntry, AccessTier.FREE)
        assertEquals(AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE), decision)
        assertTrue(isMeditationLocked(decision))
        assertTrue(canWatchAdToUnlockMeditation(decision))
        assertEquals(GroupBadge.AD_UNLOCK, deriveMeditationBadge(perUseEntry, decision))
    }

    @Test
    fun `a PER_USE entry is unlocked and unbadged for a Free user holding a live session grant`() {
        val key = ContentKey(ContentType.MEDITATION, perUseEntry.id)
        val grants = AdUnlockState(sessionUnlocks = setOf(key))
        val decision = decisionFor(perUseEntry, AccessTier.FREE, grants)
        assertEquals(AccessDecision.UnlockedByAd(AdUnlockPolicy.PER_USE), decision)
        assertFalse(isMeditationLocked(decision))
        assertFalse(canWatchAdToUnlockMeditation(decision))
        assertNull(deriveMeditationBadge(perUseEntry, decision))
    }

    @Test
    fun `a PER_USE entry is unlocked and unbadged for a Pro user regardless of grants`() {
        val decision = decisionFor(perUseEntry, AccessTier.PRO)
        assertEquals(AccessDecision.Unlocked, decision)
        assertFalse(isMeditationLocked(decision))
        assertNull(deriveMeditationBadge(perUseEntry, decision))
    }

    // --- ProOrAdTrial entry: FREE-no-record / FREE-active-trial / FREE-spent-trial / PRO ---------

    @Test
    fun `a ONE_TIME_TRIAL entry is locked but ad-unlockable, and AD_UNLOCK-badged, for a Free user with no trial record`() {
        val decision = decisionFor(trialEntry, AccessTier.FREE)
        assertEquals(AccessDecision.LockedAdUnlockable(AdUnlockPolicy.ONE_TIME_TRIAL), decision)
        assertTrue(isMeditationLocked(decision))
        assertTrue(canWatchAdToUnlockMeditation(decision))
        assertEquals(GroupBadge.AD_UNLOCK, deriveMeditationBadge(trialEntry, decision))
    }

    @Test
    fun `a ONE_TIME_TRIAL entry is unlocked and unbadged for a Free user holding an unexpired trial`() {
        val key = ContentKey(ContentType.MEDITATION, trialEntry.id)
        val record = AdUnlockRecord(key = key, grantedAtMillis = now - 1_000, expiresAtMillis = null)
        val grants = AdUnlockState(durableUnlocks = mapOf(key to record))
        val decision = decisionFor(trialEntry, AccessTier.FREE, grants)
        assertEquals(AccessDecision.UnlockedByAd(AdUnlockPolicy.ONE_TIME_TRIAL), decision)
        assertFalse(isMeditationLocked(decision))
        assertFalse(canWatchAdToUnlockMeditation(decision))
        assertNull(deriveMeditationBadge(trialEntry, decision))
    }

    @Test
    fun `a spent ONE_TIME_TRIAL entry is locked with no ad path, and never re-offers the ad CTA`() {
        val key = ContentKey(ContentType.MEDITATION, trialEntry.id)
        val expiredRecord = AdUnlockRecord(key = key, grantedAtMillis = now - 10_000, expiresAtMillis = now - 1_000)
        val grants = AdUnlockState(durableUnlocks = mapOf(key to expiredRecord))
        val decision = decisionFor(trialEntry, AccessTier.FREE, grants)
        assertEquals(AccessDecision.LockedNeedsPro, decision)
        assertTrue(isMeditationLocked(decision))
        // The trial was already spent — it must never be re-offered as an ad CTA, regardless of
        // what badge is shown.
        assertFalse(canWatchAdToUnlockMeditation(decision))
        // deriveMeditationBadge branches on the resolved AccessDecision, not the entry's static
        // policy declaration, so a spent ONE_TIME_TRIAL (resolved to LockedNeedsPro) correctly
        // shows GroupBadge.PREMIUM — no ad path remains, upgrade is the only route (REQ-5.3).
        assertEquals(GroupBadge.PREMIUM, deriveMeditationBadge(trialEntry, decision))
    }

    @Test
    fun `a ONE_TIME_TRIAL entry is unlocked and unbadged for a Pro user regardless of trial state`() {
        val decision = decisionFor(trialEntry, AccessTier.PRO)
        assertEquals(AccessDecision.Unlocked, decision)
        assertFalse(isMeditationLocked(decision))
        assertNull(deriveMeditationBadge(trialEntry, decision))
    }
}
