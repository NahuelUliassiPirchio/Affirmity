package com.pirxhio.affirmity.analytics

import com.pirxhio.affirmity.access.AccessDecision

/**
 * Maps [AccessDecision] to its wire-safe [AccessDecisionValue] (design D4). An extension function,
 * not a member, so `access/AccessDecision.kt` stays byte-for-byte unchanged (spec §8 acceptance
 * criterion). All four cases are reachable — asserted directly by REQ-6.2's tests.
 */
fun AccessDecision.provenance(): AccessDecisionValue = when (this) {
    AccessDecision.Unlocked -> AccessDecisionValue.UNLOCKED
    is AccessDecision.UnlockedByAd -> AccessDecisionValue.UNLOCKED_BY_AD
    AccessDecision.LockedNeedsPro -> AccessDecisionValue.LOCKED_NEEDS_PRO
    is AccessDecision.LockedAdUnlockable -> AccessDecisionValue.LOCKED_AD_UNLOCKABLE
}
