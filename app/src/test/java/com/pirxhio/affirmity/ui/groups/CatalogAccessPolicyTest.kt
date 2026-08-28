package com.pirxhio.affirmity.ui.groups

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockRecord
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.access.isUnlocked
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RED-first for design D6's two-level facade. `catalogAccessDecision` short-circuits on
 * `alwaysSelected` FIRST, preserving `GroupAccessPolicy`'s "PERSONALIZADAS_GROUP is never locked"
 * regression guard.
 */
class CatalogAccessPolicyTest {

    private val freeGroup = AffirmationGroup(
        id = "free_group",
        titleRes = R.string.affirmation_group_personalizadas_title,
        descriptionRes = R.string.affirmation_group_personalizadas_description,
        icon = Icons.Filled.Favorite,
        access = ContentAccess.Free,
    )

    private val proGroup = freeGroup.copy(id = "pro_group", access = ContentAccess.Pro)

    private fun collection(access: ContentAccess) = CatalogCollection(
        id = "collection",
        universeId = "free_group",
        themeId = "theme",
        access = access,
        order = 1,
    )

    @Test
    fun `alwaysSelected short-circuits first regardless of collection access`() {
        val alwaysSelectedGroup = freeGroup.copy(alwaysSelected = true, access = ContentAccess.Pro)
        val decision = catalogAccessDecision(
            group = alwaysSelectedGroup,
            collection = collection(ContentAccess.Pro),
            tier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(AccessDecision.Unlocked, decision)
    }

    @Test
    fun `free collection in a Pro group is locked - D6(a) regression guard`() {
        val decision = catalogAccessDecision(
            group = proGroup,
            collection = collection(ContentAccess.Free),
            tier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(false, decision.isUnlocked)
    }

    @Test
    fun `Pro collection in a Free group is locked`() {
        val decision = catalogAccessDecision(
            group = freeGroup,
            collection = collection(ContentAccess.Pro),
            tier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(false, decision.isUnlocked)
    }

    @Test
    fun `Free group and Free collection is unlocked`() {
        val decision = catalogAccessDecision(
            group = freeGroup,
            collection = collection(ContentAccess.Free),
            tier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(true, decision.isUnlocked)
    }

    @Test
    fun `a null collection contributes Unlocked so the group gate alone decides`() {
        val decisionFreeGroup = catalogAccessDecision(
            group = freeGroup,
            collection = null,
            tier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(true, decisionFreeGroup.isUnlocked)

        val decisionProGroup = catalogAccessDecision(
            group = proGroup,
            collection = null,
            tier = AccessTier.FREE,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(false, decisionProGroup.isUnlocked)
    }

    @Test
    fun `Pro user unlocks a Pro collection under a Free group`() {
        val decision = catalogAccessDecision(
            group = freeGroup,
            collection = collection(ContentAccess.Pro),
            tier = AccessTier.PRO,
            grants = AdUnlockState(),
            nowMillis = 0L,
        )
        assertEquals(true, decision.isUnlocked)
    }

    // --- deriveCatalogBadge (design D19, task 5.1) ------------------------------------------

    @Test
    fun `unlocked group with isPartiallyLocked true derives PARTIALLY_LOCKED`() {
        val decision = groupAccessDecision(freeGroup, AccessTier.FREE, AdUnlockState(), 0L)
        val badge = deriveCatalogBadge(freeGroup, decision, isPartiallyLocked = true)
        assertEquals(GroupBadge.PARTIALLY_LOCKED, badge)
    }

    @Test
    fun `unlocked group with isPartiallyLocked false derives null`() {
        val decision = groupAccessDecision(freeGroup, AccessTier.FREE, AdUnlockState(), 0L)
        val badge = deriveCatalogBadge(freeGroup, decision, isPartiallyLocked = false)
        assertEquals(null, badge)
    }

    @Test
    fun `a locked group never derives PARTIALLY_LOCKED, even when isPartiallyLocked is true`() {
        val decision = groupAccessDecision(proGroup, AccessTier.FREE, AdUnlockState(), 0L)
        val badge = deriveCatalogBadge(proGroup, decision, isPartiallyLocked = true)
        assertEquals(GroupBadge.PREMIUM, badge)
    }

    @Test
    fun `personalizadas keeps its own PREMIUM override, never PARTIALLY_LOCKED`() {
        val decision = groupAccessDecision(PERSONALIZADAS_GROUP, AccessTier.FREE, AdUnlockState(), 0L)
        val badge = deriveCatalogBadge(PERSONALIZADAS_GROUP, decision, isPartiallyLocked = true)
        assertEquals(GroupBadge.PREMIUM, badge)
    }

    // --- partiallyLockedGroupIds (design D19, task 5.2) -------------------------------------

    @Test
    fun `PRO tier is always emptySet, zero collection resolutions`() {
        val ids = partiallyLockedGroupIds(AccessTier.PRO, AdUnlockState(), nowMillis = 0L)
        assertEquals(emptySet<String>(), ids)
    }

    @Test
    fun `FREE tier with empty grants returns exactly CATALOG_GATED_GROUP_IDS`() {
        val ids = partiallyLockedGroupIds(AccessTier.FREE, AdUnlockState(), nowMillis = 0L)
        assertEquals(CATALOG_GATED_GROUP_IDS, ids)
    }

    @Test
    fun `CATALOG_GATED_GROUP_IDS matches the set derived from catalogCollections at runtime`() {
        val derived = catalogCollections()
            .filter { it.access != null && it.access != ContentAccess.Free }
            .map { it.universeId }
            .toSet()
        assertEquals(derived, CATALOG_GATED_GROUP_IDS)
    }

    @Test
    fun `FREE with a live grant covering the only gated collection of a universe drops it out`() {
        val universeId = CATALOG_GATED_GROUP_IDS.first()
        val gatedCollection = catalogCollections().first {
            it.universeId == universeId && it.access != null && it.access != ContentAccess.Free
        }
        val key = ContentKey(ContentType.AFFIRMATION_COLLECTION, gatedCollection.id)
        val grants = AdUnlockState(
            durableUnlocks = mapOf(key to AdUnlockRecord(key, grantedAtMillis = 0L, expiresAtMillis = null)),
        )
        val ids = partiallyLockedGroupIds(AccessTier.FREE, grants, nowMillis = 0L)
        // Only drops out if the unlocked collection was the ONLY gated one in that universe.
        val remainingGatedInUniverse = catalogCollections().count {
            it.universeId == universeId && it.access != null && it.access != ContentAccess.Free && it.id != gatedCollection.id
        }
        if (remainingGatedInUniverse == 0) {
            assertEquals(false, universeId in ids)
        } else {
            assertEquals(true, universeId in ids)
        }
    }

    @Test
    fun `an expired timed grant does not clear the gate - universe stays partially locked`() {
        val universeId = CATALOG_GATED_GROUP_IDS.first()
        val gatedCollection = catalogCollections().first {
            it.universeId == universeId && it.access != null && it.access != ContentAccess.Free
        }
        val key = ContentKey(ContentType.AFFIRMATION_COLLECTION, gatedCollection.id)
        val grants = AdUnlockState(
            timedUnlocks = mapOf(key to AdUnlockRecord(key, grantedAtMillis = 0L, expiresAtMillis = 1L)),
        )
        val ids = partiallyLockedGroupIds(AccessTier.FREE, grants, nowMillis = 100L)
        assertEquals(true, universeId in ids)
    }
}
