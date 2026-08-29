package com.pirxhio.affirmity.ui.groups

import com.pirxhio.affirmity.access.AccessDecision
import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockPolicy
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.access.isUnlocked
import com.pirxhio.affirmity.access.offersAdUnlock
import com.pirxhio.affirmity.access.resolveAccess

/**
 * Group-level facade over the content-type-agnostic [resolveAccess] (design §6). Its ONLY
 * remaining job is the one concept the access model deliberately does not know about:
 * [AffirmationGroup.alwaysSelected]. The FREE/PREMIUM/AD_SUPPORTED translation table this file
 * used to be is gone -- one [com.pirxhio.affirmity.access.ContentAccess] now serves both sides.
 *
 * [AffirmationGroup.alwaysSelected] short-circuits **first**, so `PERSONALIZADAS_GROUP` always
 * resolves [AccessDecision.Unlocked] and is never locked at either tier (spec's "PERSONALIZADAS_GROUP
 * is never locked" regression guard -- covered by `GroupAccessPolicyTest`). As of a TEMPORARY
 * dogfooding relaxation it IS toggleable again -- see [isToggleable]'s KDoc.
 */
fun groupAccessDecision(
    group: AffirmationGroup,
    tier: AccessTier,
    grants: AdUnlockState,
    nowMillis: Long,
): AccessDecision =
    if (group.alwaysSelected) {
        AccessDecision.Unlocked
    } else {
        resolveAccess(
            key = ContentKey(ContentType.AFFIRMATION_GROUP, group.id),
            content = group.access,
            userTier = tier,
            grants = grants,
            nowMillis = nowMillis,
        )
    }

fun isLocked(decision: AccessDecision): Boolean = !decision.isUnlocked

/** TEMPORARY (dogfooding request): `alwaysSelected` groups used to be permanently non-toggleable
 * here, keeping `PERSONALIZADAS_GROUP` checked forever. That's relaxed for now so it can be
 * unchecked like any other unlocked group -- it still always resolves [AccessDecision.Unlocked]
 * (see [groupAccessDecision]) and still always shows its badge (see [deriveBadge]), only the
 * "cannot toggle" restriction is lifted. [resolveSelectedGroupIds] was changed to match: it no
 * longer force-re-adds `personalizadas` into an otherwise-healthy persisted selection. To restore
 * the old permanent-checked behavior, reinstate `!group.alwaysSelected &&` here. */
fun isToggleable(group: AffirmationGroup, decision: AccessDecision): Boolean =
    decision.isUnlocked

/** Spec 5's CTA gate -- the ONLY predicate that may show a "watch an ad" affordance. */
fun canWatchAdToUnlock(decision: AccessDecision): Boolean = decision.offersAdUnlock

/**
 * Both product decisions from spec §0, folded into a single formula:
 * 1. `PERSONALIZADAS_GROUP` shows its Premium badge unconditionally, at every tier.
 * 2. Every OTHER (non-`alwaysSelected`) group hides its badge once the resolved [decision] is
 *    unlocked -- a Pro user, or a Free user holding an active ad grant, never sees a badge on
 *    already-unlocked content.
 *
 * `alwaysSelected` groups are exempt from rule 2: they always resolve [AccessDecision.Unlocked]
 * (see [groupAccessDecision]), so applying rule 2 to them would unconditionally hide their badge,
 * silently defeating rule 1. This exact branching order is spec §0's reconciliation rule.
 */
fun deriveBadge(group: AffirmationGroup, decision: AccessDecision): GroupBadge? =
    if (group.alwaysSelected) {
        group.badgeOverride
    } else if (decision.isUnlocked) {
        null
    } else {
        group.badgeOverride ?: derivedBadgeFrom(group)
    }

/** Declaration-driven fallback badge when [AffirmationGroup.badgeOverride] is unset: PREMIUM for
 * no-ad-path Pro content, AD_UNLOCK when an ad CTA exists. Free content never reaches here because
 * [deriveBadge] only calls this while [decision] is locked, and FREE content is never locked. */
private fun derivedBadgeFrom(group: AffirmationGroup): GroupBadge? = when {
    group.access.requiredTier == AccessTier.FREE -> null
    group.access.adUnlock == AdUnlockPolicy.NONE -> GroupBadge.PREMIUM
    else -> GroupBadge.AD_UNLOCK
}
