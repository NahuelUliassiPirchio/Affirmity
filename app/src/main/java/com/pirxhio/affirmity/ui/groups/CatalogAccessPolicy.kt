package com.pirxhio.affirmity.ui.groups

import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentAccess
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.access.isUnlocked
import com.pirxhio.affirmity.access.mostRestrictive
import com.pirxhio.affirmity.access.resolveAccess

/**
 * Two-level facade (design D6). `alwaysSelected` short-circuits FIRST, preserving
 * `GroupAccessPolicy`'s "PERSONALIZADAS_GROUP is never locked" regression guard. A [collection] of
 * `null` (unknown/archived) contributes [AccessDecision.Unlocked], so the group gate alone decides
 * -- an unknown collection can never be MORE permissive than its group.
 */
fun catalogAccessDecision(
    group: AffirmationGroup,
    collection: CatalogCollection?,
    tier: AccessTier,
    grants: AdUnlockState,
    nowMillis: Long,
): AccessDecision {
    if (group.alwaysSelected) return AccessDecision.Unlocked

    val groupDecision = resolveAccess(
        key = ContentKey(ContentType.AFFIRMATION_GROUP, group.id),
        content = group.access,
        userTier = tier,
        grants = grants,
        nowMillis = nowMillis,
    )
    if (collection == null) return groupDecision

    val collectionDecision = resolveAccess(
        key = ContentKey(ContentType.AFFIRMATION_COLLECTION, collection.id),
        content = collection.access,
        userTier = tier,
        grants = grants,
        nowMillis = nowMillis,
    )
    return mostRestrictive(groupDecision, collectionDecision)
}

/**
 * Catalog-aware badge (design D19). Strictly a FALLBACK layered over [deriveBadge]:
 *  - `alwaysSelected` (personalizadas) -> its `badgeOverride`, unchanged, and NEVER partial.
 *  - a locked group -> PREMIUM / AD_UNLOCK, unchanged. A fully-locked row must not be downgraded
 *    to "partially" locked.
 *  - unlocked group + >=1 locked collection -> PARTIALLY_LOCKED (the only new outcome).
 *  - unlocked group, nothing locked underneath -> null, unchanged.
 */
fun deriveCatalogBadge(
    group: AffirmationGroup,
    decision: AccessDecision,
    isPartiallyLocked: Boolean,
): GroupBadge? =
    deriveBadge(group, decision)
        ?: GroupBadge.PARTIALLY_LOCKED.takeIf { isPartiallyLocked && !group.alwaysSelected }

/**
 * Which universes currently read as partially locked, for THIS user (design D19).
 *
 * Partial lock is user-dependent, so this cannot be a static flag on [AffirmationGroup]. The cost
 * is staged so the two common cases never resolve a single collection:
 *
 *  1. `tier == PRO`            -> `emptySet()`. One enum comparison. Nothing under any universe is
 *                                 locked for a Pro user, by definition.
 *  2. FREE with NO collection-scoped grant -> [CATALOG_GATED_GROUP_IDS] verbatim. This is a
 *                                 GENERATED compile-time constant (see CatalogTaxonomy), so the
 *                                 answer costs a set copy and ZERO access resolution. This is the
 *                                 overwhelmingly common Free path.
 *  3. FREE holding >=1 `AFFIRMATION_COLLECTION` grant -> resolve, but only over the collections
 *                                 that are actually gated (<=150, never the 226) and only for
 *                                 groups already in the static set. Short-circuits per group on the
 *                                 first still-locked collection.
 *
 * Deliberately returns a SET rather than a per-group predicate: the caller memoizes one value for
 * the whole sheet instead of doing work inside a `LazyColumn` item.
 */
fun partiallyLockedGroupIds(
    tier: AccessTier,
    grants: AdUnlockState,
    nowMillis: Long,
): Set<String> {
    if (tier == AccessTier.PRO) return emptySet()

    val hasCollectionScopedGrant = (grants.durableUnlocks.keys + grants.timedUnlocks.keys)
        .any { it.type == ContentType.AFFIRMATION_COLLECTION }
    if (!hasCollectionScopedGrant) return CATALOG_GATED_GROUP_IDS

    val collectionsByUniverse = catalogCollections()
        .filter { it.universeId in CATALOG_GATED_GROUP_IDS }
        .groupBy { it.universeId }

    return CATALOG_GATED_GROUP_IDS.filterTo(mutableSetOf()) { universeId ->
        collectionsByUniverse[universeId].orEmpty().any { collection ->
            val access = collection.access ?: return@any false
            if (access == ContentAccess.Free) return@any false
            !resolveAccess(
                key = ContentKey(ContentType.AFFIRMATION_COLLECTION, collection.id),
                content = access,
                userTier = tier,
                grants = grants,
                nowMillis = nowMillis,
            ).isUnlocked
        }
    }
}
