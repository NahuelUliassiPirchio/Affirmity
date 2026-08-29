package com.pirxhio.affirmity.access

/**
 * Most-restrictive composition of two independent gates (design D6). Pure, total, commutative,
 * associative, with [AccessDecision.Unlocked] as identity -- all four asserted in
 * `AccessCombinationTest`'s full 4x4 truth table.
 *
 * NOT a change to [resolveAccess]: this composes its OUTPUT, so every existing single-level
 * caller (`groupAccessDecision`, `meditationAccessDecision`, `customAffirmationDecision`) is
 * untouched, and the group gate remains an invariant a collection can never override.
 *
 * Implemented as a total order by restrictiveness rank -- the lower rank always wins, which makes
 * commutativity and associativity structural rather than something to special-case per branch.
 */
fun mostRestrictive(a: AccessDecision, b: AccessDecision): AccessDecision =
    if (restrictivenessRank(a) <= restrictivenessRank(b)) a else b

/** Lower is MORE restrictive. Precedence (design D6):
 * `LockedNeedsPro` (absorbing) < any `LockedAdUnlockable` < any `UnlockedByAd` < `Unlocked`
 * (identity). Within the ad-gated bands, the stricter POLICY wins on the total order
 * `ONE_TIME_TRIAL` > `TIMED_REPEATABLE` > `PER_USE` (once-ever is stricter than
 * once-per-window, which is stricter than always-re-earnable). */
private fun restrictivenessRank(decision: AccessDecision): Int = when (decision) {
    AccessDecision.LockedNeedsPro -> 0
    is AccessDecision.LockedAdUnlockable -> 1 + policyStrictnessRank(decision.policy)
    is AccessDecision.UnlockedByAd -> 4 + policyStrictnessRank(decision.policy)
    AccessDecision.Unlocked -> 7
}

private fun policyStrictnessRank(policy: AdUnlockPolicy): Int = when (policy) {
    AdUnlockPolicy.ONE_TIME_TRIAL -> 0
    AdUnlockPolicy.TIMED_REPEATABLE -> 1
    AdUnlockPolicy.PER_USE -> 2
    AdUnlockPolicy.NONE -> 2 // never actually produced by resolveAccess, kept for exhaustiveness
}
