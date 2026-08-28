package com.pirxhio.affirmity.ui.groups

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockPolicy
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.access.isUnlocked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers [groupAccessDecision]/[isLocked]/[isToggleable]/[deriveBadge] (design §6/§7, spec §0)
 * across the full tier x policy x grant-state matrix, with explicit regression guards for
 * `PERSONALIZADAS_GROUP`'s `alwaysSelected` short-circuit and both spec §0 badge decisions.
 *
 * Fixtures below are LOCALLY constructed (design D17/task 4.6.1), not sourced from
 * `defaultAffirmationGroups()` by literal id -- the 3 legacy placeholder groups this suite
 * originally borrowed (`bienestar`/`autocuidado`/`fuerza_de_voluntad`) were deleted by the catalog
 * change. This suite only needs one representative of each access shape (Free / Pro-only /
 * Pro-or-ad-PER_USE), so it builds its own, decoupled from production group wiring. `titleRes`/
 * `descriptionRes` are placeholders -- irrelevant to these pure decision-resolution tests. */
class GroupAccessPolicyTest {

    private val bienestar = AffirmationGroup( // Free
        id = "test_free_group",
        titleRes = R.string.app_name,
        descriptionRes = R.string.app_name,
        icon = Icons.Filled.Favorite,
        access = ContentAccess.Free,
    )
    private val autocuidado = AffirmationGroup( // Pro-only
        id = "test_pro_only_group",
        titleRes = R.string.app_name,
        descriptionRes = R.string.app_name,
        icon = Icons.Filled.Favorite,
        access = ContentAccess.Pro,
    )
    private val fuerzaDeVoluntad = AffirmationGroup( // Pro or ad (PER_USE)
        id = "test_pro_or_ad_group",
        titleRes = R.string.app_name,
        descriptionRes = R.string.app_name,
        icon = Icons.Filled.Favorite,
        access = ContentAccess.ProOrAdPerUse,
    )

    private val noGrants = AdUnlockState()
    private val now = 1_000_000L

    private fun decisionFor(group: AffirmationGroup, tier: AccessTier, grants: AdUnlockState = noGrants) =
        groupAccessDecision(group, tier, grants, now)

    // --- PERSONALIZADAS_GROUP regression guard (alwaysSelected short-circuits first) --------

    @Test
    fun `PERSONALIZADAS_GROUP always resolves Unlocked regardless of tier`() {
        assertEquals(AccessDecision.Unlocked, decisionFor(PERSONALIZADAS_GROUP, AccessTier.FREE))
        assertEquals(AccessDecision.Unlocked, decisionFor(PERSONALIZADAS_GROUP, AccessTier.PRO))
    }

    @Test
    fun `PERSONALIZADAS_GROUP is never locked for a Free user`() {
        assertFalse(isLocked(decisionFor(PERSONALIZADAS_GROUP, AccessTier.FREE)))
    }

    @Test
    fun `PERSONALIZADAS_GROUP is never locked for a Pro user`() {
        assertFalse(isLocked(decisionFor(PERSONALIZADAS_GROUP, AccessTier.PRO)))
    }

    @Test
    fun `PERSONALIZADAS_GROUP is never toggleable for a Free user`() {
        assertFalse(isToggleable(PERSONALIZADAS_GROUP, decisionFor(PERSONALIZADAS_GROUP, AccessTier.FREE)))
    }

    @Test
    fun `PERSONALIZADAS_GROUP is never toggleable for a Pro user`() {
        assertFalse(isToggleable(PERSONALIZADAS_GROUP, decisionFor(PERSONALIZADAS_GROUP, AccessTier.PRO)))
    }

    // --- FREE-access group (bienestar): unlocked/toggleable at both tiers -------------------

    @Test
    fun `a FREE-access group is unlocked and toggleable for a Free user`() {
        val decision = decisionFor(bienestar, AccessTier.FREE)
        assertTrue(decision.isUnlocked)
        assertFalse(isLocked(decision))
        assertTrue(isToggleable(bienestar, decision))
    }

    @Test
    fun `a FREE-access group is unlocked and toggleable for a Pro user`() {
        val decision = decisionFor(bienestar, AccessTier.PRO)
        assertTrue(decision.isUnlocked)
        assertFalse(isLocked(decision))
        assertTrue(isToggleable(bienestar, decision))
    }

    // --- Pro-only group, no ad path (autocuidado): locked for Free, unlocked+toggleable for Pro

    @Test
    fun `a Pro-only group is locked and not toggleable for a Free user`() {
        val decision = decisionFor(autocuidado, AccessTier.FREE)
        assertEquals(AccessDecision.LockedNeedsPro, decision)
        assertTrue(isLocked(decision))
        assertFalse(isToggleable(autocuidado, decision))
    }

    @Test
    fun `a Pro-only group is unlocked and toggleable for a Pro user`() {
        val decision = decisionFor(autocuidado, AccessTier.PRO)
        assertEquals(AccessDecision.Unlocked, decision)
        assertFalse(isLocked(decision))
        assertTrue(isToggleable(autocuidado, decision))
    }

    // --- Pro-or-ad group (fuerza_de_voluntad): FREE-no-grant / FREE-with-grant / PRO cases ----

    @Test
    fun `fuerza_de_voluntad is locked but ad-unlockable for a Free user with no grant`() {
        val decision = decisionFor(fuerzaDeVoluntad, AccessTier.FREE)
        assertEquals(AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE), decision)
        assertTrue(isLocked(decision))
        assertFalse(isToggleable(fuerzaDeVoluntad, decision))
        assertTrue(canWatchAdToUnlock(decision))
    }

    @Test
    fun `fuerza_de_voluntad is unlocked and toggleable for a Free user holding a live grant`() {
        val key = ContentKey(ContentType.AFFIRMATION_GROUP, fuerzaDeVoluntad.id)
        val grants = AdUnlockState(sessionUnlocks = setOf(key))
        val decision = decisionFor(fuerzaDeVoluntad, AccessTier.FREE, grants)
        assertEquals(AccessDecision.UnlockedByAd(AdUnlockPolicy.PER_USE), decision)
        assertFalse(isLocked(decision))
        assertTrue(isToggleable(fuerzaDeVoluntad, decision))
        assertFalse(canWatchAdToUnlock(decision))
    }

    @Test
    fun `fuerza_de_voluntad is unlocked and toggleable for a Pro user regardless of grants`() {
        val decision = decisionFor(fuerzaDeVoluntad, AccessTier.PRO)
        assertEquals(AccessDecision.Unlocked, decision)
        assertFalse(isLocked(decision))
        assertTrue(isToggleable(fuerzaDeVoluntad, decision))
        assertFalse(canWatchAdToUnlock(decision))
    }

    // --- Spec §0 decision-1: PERSONALIZADAS_GROUP shows its Premium badge unconditionally ----

    @Test
    fun `PERSONALIZADAS_GROUP shows the Premium badge at every tier, even though it is always Unlocked`() {
        assertEquals(GroupBadge.PREMIUM, deriveBadge(PERSONALIZADAS_GROUP, decisionFor(PERSONALIZADAS_GROUP, AccessTier.FREE)))
        assertEquals(GroupBadge.PREMIUM, deriveBadge(PERSONALIZADAS_GROUP, decisionFor(PERSONALIZADAS_GROUP, AccessTier.PRO)))
    }

    // --- Spec §0 decision-2: badges hide for anyone already unlocked, on every OTHER group ---

    @Test
    fun `a locked non-alwaysSelected group shows its badge, but the badge disappears once unlocked (Pro tier path)`() {
        val lockedDecision = decisionFor(fuerzaDeVoluntad, AccessTier.FREE)
        assertEquals(GroupBadge.AD_UNLOCK, deriveBadge(fuerzaDeVoluntad, lockedDecision))

        val unlockedDecision = decisionFor(fuerzaDeVoluntad, AccessTier.PRO)
        assertNull(deriveBadge(fuerzaDeVoluntad, unlockedDecision))
    }

    @Test
    fun `a locked non-alwaysSelected group's badge disappears once unlocked via an ad grant (Free-with-grant path)`() {
        val lockedDecision = decisionFor(fuerzaDeVoluntad, AccessTier.FREE)
        assertEquals(GroupBadge.AD_UNLOCK, deriveBadge(fuerzaDeVoluntad, lockedDecision))

        val key = ContentKey(ContentType.AFFIRMATION_GROUP, fuerzaDeVoluntad.id)
        val grants = AdUnlockState(sessionUnlocks = setOf(key))
        val unlockedByAdDecision = decisionFor(fuerzaDeVoluntad, AccessTier.FREE, grants)
        assertNull(deriveBadge(fuerzaDeVoluntad, unlockedByAdDecision))
    }

    @Test
    fun `a locked Pro-only group with no ad path shows the Premium badge, hidden once unlocked`() {
        val lockedDecision = decisionFor(autocuidado, AccessTier.FREE)
        assertEquals(GroupBadge.PREMIUM, deriveBadge(autocuidado, lockedDecision))

        val unlockedDecision = decisionFor(autocuidado, AccessTier.PRO)
        assertNull(deriveBadge(autocuidado, unlockedDecision))
    }

    @Test
    fun `a FREE-access group never shows a badge at either tier`() {
        assertNull(deriveBadge(bienestar, decisionFor(bienestar, AccessTier.FREE)))
        assertNull(deriveBadge(bienestar, decisionFor(bienestar, AccessTier.PRO)))
    }
}
