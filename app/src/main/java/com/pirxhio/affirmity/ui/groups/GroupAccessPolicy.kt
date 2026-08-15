package com.pirxhio.affirmity.ui.groups

import com.pirxhio.affirmity.access.AccessTier

/**
 * Single source of truth for group lock/toggle state (design.md D6). Previously duplicated
 * between `AffirmationGroupSelectorSheet.kt` and `MainActivity.kt` — both call sites now delegate
 * here so entitlement never needs to be threaded into either independently.
 *
 * [AffirmationGroup.alwaysSelected] short-circuits **first** in every branch, so
 * `PERSONALIZADAS_GROUP` is never locked and never toggleable at either tier (spec's
 * "PERSONALIZADAS_GROUP is never locked" regression guard — covered by
 * `GroupAccessPolicyTest`'s 2-tier x every-group matrix).
 *
 * `AD_SUPPORTED` is not `FREE`, so it is Pro-only per the confirmed product mapping — there is no
 * ad-unlock path in v1.
 */
fun isUnlocked(group: AffirmationGroup, tier: AccessTier): Boolean =
    group.alwaysSelected || group.access == AffirmationGroupAccess.FREE || tier == AccessTier.PRO

fun isLocked(group: AffirmationGroup, tier: AccessTier): Boolean =
    !group.alwaysSelected && !isUnlocked(group, tier)

fun isToggleable(group: AffirmationGroup, tier: AccessTier): Boolean =
    !group.alwaysSelected && isUnlocked(group, tier)
