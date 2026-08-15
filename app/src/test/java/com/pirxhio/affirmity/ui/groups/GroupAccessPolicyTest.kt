package com.pirxhio.affirmity.ui.groups

import com.pirxhio.affirmity.data.repository.EntitlementTier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the single source of lock/toggle truth (design.md D6) across both tiers and every
 * selectable group, with an explicit regression guard for `PERSONALIZADAS_GROUP`'s `alwaysSelected`
 * short-circuit (spec's "PERSONALIZADAS_GROUP is never locked" requirement). */
class GroupAccessPolicyTest {

    private val bienestar = defaultAffirmationGroups().first { it.id == "bienestar" } // FREE
    private val autocuidado = defaultAffirmationGroups().first { it.id == "autocuidado" } // PREMIUM
    private val fuerzaDeVoluntad = defaultAffirmationGroups().first { it.id == "fuerza_de_voluntad" } // AD_SUPPORTED

    // --- PERSONALIZADAS_GROUP regression guard (alwaysSelected short-circuits first) --------

    @Test
    fun `PERSONALIZADAS_GROUP is never locked for a Free user`() {
        assertFalse(isLocked(PERSONALIZADAS_GROUP, EntitlementTier.FREE))
    }

    @Test
    fun `PERSONALIZADAS_GROUP is never locked for a Pro user`() {
        assertFalse(isLocked(PERSONALIZADAS_GROUP, EntitlementTier.PRO))
    }

    @Test
    fun `PERSONALIZADAS_GROUP is never toggleable for a Free user`() {
        assertFalse(isToggleable(PERSONALIZADAS_GROUP, EntitlementTier.FREE))
    }

    @Test
    fun `PERSONALIZADAS_GROUP is never toggleable for a Pro user`() {
        assertFalse(isToggleable(PERSONALIZADAS_GROUP, EntitlementTier.PRO))
    }

    @Test
    fun `PERSONALIZADAS_GROUP is always unlocked at either tier`() {
        assertTrue(isUnlocked(PERSONALIZADAS_GROUP, EntitlementTier.FREE))
        assertTrue(isUnlocked(PERSONALIZADAS_GROUP, EntitlementTier.PRO))
    }

    // --- FREE access group (bienestar): unlocked/toggleable at both tiers -------------------

    @Test
    fun `a FREE-access group is unlocked and toggleable for a Free user`() {
        assertTrue(isUnlocked(bienestar, EntitlementTier.FREE))
        assertFalse(isLocked(bienestar, EntitlementTier.FREE))
        assertTrue(isToggleable(bienestar, EntitlementTier.FREE))
    }

    @Test
    fun `a FREE-access group is unlocked and toggleable for a Pro user`() {
        assertTrue(isUnlocked(bienestar, EntitlementTier.PRO))
        assertFalse(isLocked(bienestar, EntitlementTier.PRO))
        assertTrue(isToggleable(bienestar, EntitlementTier.PRO))
    }

    // --- PREMIUM group (autocuidado): locked for Free, unlocked+toggleable for Pro ----------

    @Test
    fun `a PREMIUM group is locked and not toggleable for a Free user`() {
        assertFalse(isUnlocked(autocuidado, EntitlementTier.FREE))
        assertTrue(isLocked(autocuidado, EntitlementTier.FREE))
        assertFalse(isToggleable(autocuidado, EntitlementTier.FREE))
    }

    @Test
    fun `a PREMIUM group is unlocked and toggleable for a Pro user`() {
        assertTrue(isUnlocked(autocuidado, EntitlementTier.PRO))
        assertFalse(isLocked(autocuidado, EntitlementTier.PRO))
        assertTrue(isToggleable(autocuidado, EntitlementTier.PRO))
    }

    // --- AD_SUPPORTED group (fuerza_de_voluntad): Pro-only, no ad-unlock path in v1 ----------

    @Test
    fun `an AD_SUPPORTED group is locked and not toggleable for a Free user`() {
        assertFalse(isUnlocked(fuerzaDeVoluntad, EntitlementTier.FREE))
        assertTrue(isLocked(fuerzaDeVoluntad, EntitlementTier.FREE))
        assertFalse(isToggleable(fuerzaDeVoluntad, EntitlementTier.FREE))
    }

    @Test
    fun `an AD_SUPPORTED group is unlocked and toggleable for a Pro user`() {
        assertTrue(isUnlocked(fuerzaDeVoluntad, EntitlementTier.PRO))
        assertFalse(isLocked(fuerzaDeVoluntad, EntitlementTier.PRO))
        assertTrue(isToggleable(fuerzaDeVoluntad, EntitlementTier.PRO))
    }
}
