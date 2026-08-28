package com.pirxhio.affirmity.ui.groups

import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
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
