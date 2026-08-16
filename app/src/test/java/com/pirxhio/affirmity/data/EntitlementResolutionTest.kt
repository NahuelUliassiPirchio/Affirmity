package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.data.local.PERSONALIZADAS_GROUP_ID
import com.pirxhio.affirmity.meditation.SessionEndReason
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

    @Test
    fun `a pro-only group covered by a durable ad grant survives the downgrade sweep`() {
        // design §10 Q4(ii): a ONE_TIME_TRIAL grant legitimately survives a downgrade -- only
        // this carve-out id is exempted, "autocuidado" is still stripped normally.
        val result = deselectLockedGroups(
            selected = setOf(PERSONALIZADAS_GROUP_ID, "autocuidado", "fuerza_de_voluntad"),
            proOnlyIds = setOf("autocuidado", "fuerza_de_voluntad"),
            defaultThematicIds = setOf("bienestar"),
            adUnlockedIds = setOf("fuerza_de_voluntad"),
        )
        assertEquals(setOf(PERSONALIZADAS_GROUP_ID, "fuerza_de_voluntad"), result)
    }

    // --- retainSelectionScopedUnlocks ---------------------------------------------------------

    @Test
    fun `retainSelectionScopedUnlocks drops an affirmation-group unlock whose group left the selection`() {
        val stillCommitted = ContentKey(ContentType.AFFIRMATION_GROUP, "fuerza_de_voluntad")
        val noLongerCommitted = ContentKey(ContentType.AFFIRMATION_GROUP, "autocuidado")
        val result = retainSelectionScopedUnlocks(
            unlocks = setOf(stillCommitted, noLongerCommitted),
            committedGroupIds = setOf(PERSONALIZADAS_GROUP_ID, "fuerza_de_voluntad"),
        )
        assertEquals(setOf(stillCommitted), result)
    }

    @Test
    fun `retainSelectionScopedUnlocks leaves non-affirmation-group unlocks untouched`() {
        val meditationUnlock = ContentKey(ContentType.MEDITATION, "some_meditation")
        val result = retainSelectionScopedUnlocks(
            unlocks = setOf(meditationUnlock),
            committedGroupIds = emptySet(),
        )
        assertEquals(setOf(meditationUnlock), result)
    }

    // --- consumePlaybackScopedUnlock -----------------------------------------------------------

    @Test
    fun `consumePlaybackScopedUnlock removes a MEDITATION key on Completed`() {
        val key = ContentKey(ContentType.MEDITATION, "enfoque")
        val result = consumePlaybackScopedUnlock(
            unlocks = setOf(key), key = key, reason = SessionEndReason.Completed,
        )
        assertEquals(emptySet<ContentKey>(), result)
    }

    @Test
    fun `consumePlaybackScopedUnlock removes a MEDITATION key on Cancelled`() {
        val key = ContentKey(ContentType.MEDITATION, "enfoque")
        val result = consumePlaybackScopedUnlock(
            unlocks = setOf(key), key = key, reason = SessionEndReason.Cancelled,
        )
        assertEquals(emptySet<ContentKey>(), result)
    }

    @Test
    fun `consumePlaybackScopedUnlock leaves an AFFIRMATION_GROUP key untouched under both reasons`() {
        val key = ContentKey(ContentType.AFFIRMATION_GROUP, "fuerza_de_voluntad")
        val completedResult = consumePlaybackScopedUnlock(
            unlocks = setOf(key), key = key, reason = SessionEndReason.Completed,
        )
        val cancelledResult = consumePlaybackScopedUnlock(
            unlocks = setOf(key), key = key, reason = SessionEndReason.Cancelled,
        )
        assertEquals(setOf(key), completedResult)
        assertEquals(setOf(key), cancelledResult)
    }

    @Test
    fun `consumePlaybackScopedUnlock is a no-op for a key not in the set`() {
        val absentKey = ContentKey(ContentType.MEDITATION, "enfoque")
        val result = consumePlaybackScopedUnlock(
            unlocks = emptySet(), key = absentKey, reason = SessionEndReason.Completed,
        )
        assertEquals(emptySet<ContentKey>(), result)
    }

    @Test
    fun `consumePlaybackScopedUnlock leaves other meditation keys untouched`() {
        val target = ContentKey(ContentType.MEDITATION, "enfoque")
        val other = ContentKey(ContentType.MEDITATION, "dormir")
        val result = consumePlaybackScopedUnlock(
            unlocks = setOf(target, other), key = target, reason = SessionEndReason.Completed,
        )
        assertEquals(setOf(other), result)
    }
}
