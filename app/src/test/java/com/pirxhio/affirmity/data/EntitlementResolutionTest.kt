package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the pure entitlement logic extracted for testability without Firestore/Compose
 * (design.md D5/D8): tier resolution from the raw stored doc (expiry boundary) and the
 * auto-deselect-on-downgrade invariant. */
class EntitlementResolutionTest {

    // --- resolveTier -----------------------------------------------------------------------

    @Test
    fun `null doc resolves to Free`() {
        assertEquals(AccessTier.FREE, resolveTier(doc = null, nowMillis = 1_000L))
    }

    @Test
    fun `a doc with tier free resolves to Free regardless of expiry`() {
        val doc = RawEntitlementDoc(tier = "free", expiryTimeMillis = 5_000L)
        assertEquals(AccessTier.FREE, resolveTier(doc, nowMillis = 1_000L))
    }

    @Test
    fun `a pro doc not yet expired resolves to Pro`() {
        val doc = RawEntitlementDoc(tier = "pro", expiryTimeMillis = 5_000L)
        assertEquals(AccessTier.PRO, resolveTier(doc, nowMillis = 4_999L))
    }

    @Test
    fun `a pro doc exactly at its expiry instant is downgraded to Free`() {
        val doc = RawEntitlementDoc(tier = "pro", expiryTimeMillis = 5_000L)
        assertEquals(AccessTier.FREE, resolveTier(doc, nowMillis = 5_000L))
    }

    @Test
    fun `a pro doc past its expiry resolves to Free`() {
        val doc = RawEntitlementDoc(tier = "pro", expiryTimeMillis = 5_000L)
        assertEquals(AccessTier.FREE, resolveTier(doc, nowMillis = 5_001L))
    }

    @Test
    fun `a pro doc with no expiry (null) never downgrades on time alone`() {
        val doc = RawEntitlementDoc(tier = "pro", expiryTimeMillis = null)
        assertEquals(AccessTier.PRO, resolveTier(doc, nowMillis = Long.MAX_VALUE))
    }

    // --- deselectLockedGroups ----------------------------------------------------------------

    @Test
    fun `removes only the pro-only groups from the selection`() {
        val result = deselectLockedGroups(
            selected = setOf(PERSONALIZADAS_GROUP_ID, "autocuidado", "bienestar"),
            proOnlyIds = setOf("autocuidado", "fuerza_de_voluntad"),
            defaultThematicIds = setOf("bienestar"),
        )
        // bienestar (thematic, still selected) satisfies the invariant on its own -- no fallback needed.
        assertEquals(setOf(PERSONALIZADAS_GROUP_ID, "bienestar"), result)
    }

    @Test
    fun `falls back to the default thematic ids when deselection empties the thematic selection`() {
        val result = deselectLockedGroups(
            selected = setOf(PERSONALIZADAS_GROUP_ID, "autocuidado"),
            proOnlyIds = setOf("autocuidado", "fuerza_de_voluntad"),
            defaultThematicIds = setOf("bienestar"),
        )
        assertEquals(setOf(PERSONALIZADAS_GROUP_ID, "bienestar"), result)
    }

    @Test
    fun `is a no-op when no pro-only group was selected`() {
        val result = deselectLockedGroups(
            selected = setOf(PERSONALIZADAS_GROUP_ID, "bienestar"),
            proOnlyIds = setOf("autocuidado", "fuerza_de_voluntad"),
            defaultThematicIds = setOf("bienestar"),
        )
        assertEquals(setOf(PERSONALIZADAS_GROUP_ID, "bienestar"), result)
    }
}
