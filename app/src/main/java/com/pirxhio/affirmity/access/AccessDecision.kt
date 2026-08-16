package com.pirxhio.affirmity.access

/**
 * Confirmed over [Boolean], refined to four cases. A [Boolean] forces every call site to
 * re-derive which affordance to show (upgrade CTA vs. ad CTA) — exactly the duplication
 * `GroupAccessPolicy`'s own doc comment says it exists to eliminate. Four cases, not three,
 * because *how* something got unlocked is load-bearing: the selector row's "unlocked by ad"
 * affordance, Spec 6's analytics provenance, and Q4's downgrade carve-out all need to distinguish
 * an entitled unlock from a granted one.
 */
sealed interface AccessDecision {
    /** Entitled: the user's tier satisfies the requirement (or the content is free). */
    data object Unlocked : AccessDecision

    /** Not entitled, but a live ad grant covers it right now. [policy] says which kind. */
    data class UnlockedByAd(val policy: AdUnlockPolicy) : AccessDecision

    /** Locked with no ad path — upgrade is the only route. Also the terminal state of a spent
     *  ONE_TIME_TRIAL, which must never re-offer the ad. */
    data object LockedNeedsPro : AccessDecision

    /** Locked, but an ad CTA is available. THE ONLY state in which Spec 5 may show its CTA. */
    data class LockedAdUnlockable(val policy: AdUnlockPolicy) : AccessDecision
}

val AccessDecision.isUnlocked: Boolean
    get() = this is AccessDecision.Unlocked || this is AccessDecision.UnlockedByAd

val AccessDecision.offersAdUnlock: Boolean
    get() = this is AccessDecision.LockedAdUnlockable
