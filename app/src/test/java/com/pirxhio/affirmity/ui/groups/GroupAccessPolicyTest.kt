package com.pirxhio.affirmity.ui.groups

import com.pirxhio.affirmity.access.AccessTier
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
        assertFalse(isLocked(PERSONALIZADAS_GROUP, AccessTier.FREE))
    }

    @Test
    fun `PERSONALIZADAS_GROUP is never locked for a Pro user`() {
        assertFalse(isLocked(PERSONALIZADAS_GROUP, AccessTier.PRO))
    }

    @Test
    fun `PERSONALIZADAS_GROUP is never toggleable for a Free user`() {
        assertFalse(isToggleable(PERSONALIZADAS_GROUP, AccessTier.FREE))
    }

    @Test
    fun `PERSONALIZADAS_GROUP is never toggleable for a Pro user`() {
        assertFalse(isToggleable(PERSONALIZADAS_GROUP, AccessTier.PRO))
    }

    @Test
    fun `PERSONALIZADAS_GROUP is always unlocked at either tier`() {
        assertTrue(isUnlocked(PERSONALIZADAS_GROUP, AccessTier.FREE))
        assertTrue(isUnlocked(PERSONALIZADAS_GROUP, AccessTier.PRO))
    }

    // --- FREE access group (bienestar): unlocked/toggleable at both tiers -------------------

    @Test
    fun `a FREE-access group is unlocked and toggleable for a Free user`() {
        assertTrue(isUnlocked(bienestar, AccessTier.FREE))
        assertFalse(isLocked(bienestar, AccessTier.FREE))
        assertTrue(isToggleable(bienestar, AccessTier.FREE))
    }

    @Test
    fun `a FREE-access group is unlocked and toggleable for a Pro user`() {
        assertTrue(isUnlocked(bienestar, AccessTier.PRO))
        assertFalse(isLocked(bienestar, AccessTier.PRO))
        assertTrue(isToggleable(bienestar, AccessTier.PRO))
    }

    // --- PREMIUM group (autocuidado): locked for Free, unlocked+toggleable for Pro ----------

    @Test
    fun `a PREMIUM group is locked and not toggleable for a Free user`() {
        assertFalse(isUnlocked(autocuidado, AccessTier.FREE))
        assertTrue(isLocked(autocuidado, AccessTier.FREE))
        assertFalse(isToggleable(autocuidado, AccessTier.FREE))
    }

    @Test
    fun `a PREMIUM group is unlocked and toggleable for a Pro user`() {
        assertTrue(isUnlocked(autocuidado, AccessTier.PRO))
        assertFalse(isLocked(autocuidado, AccessTier.PRO))
        assertTrue(isToggleable(autocuidado, AccessTier.PRO))
    }

    // --- AD_SUPPORTED group (fuerza_de_voluntad): Pro-only, no ad-unlock path in v1 ----------

    @Test
    fun `an AD_SUPPORTED group is locked and not toggleable for a Free user`() {
        assertFalse(isUnlocked(fuerzaDeVoluntad, AccessTier.FREE))
        assertTrue(isLocked(fuerzaDeVoluntad, AccessTier.FREE))
        assertFalse(isToggleable(fuerzaDeVoluntad, AccessTier.FREE))
    }

    @Test
    fun `an AD_SUPPORTED group is unlocked and toggleable for a Pro user`() {
        assertTrue(isUnlocked(fuerzaDeVoluntad, AccessTier.PRO))
        assertFalse(isLocked(fuerzaDeVoluntad, AccessTier.PRO))
        assertTrue(isToggleable(fuerzaDeVoluntad, AccessTier.PRO))
    }
}
