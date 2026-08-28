package com.pirxhio.affirmity.access

/** What the USER has and what the CONTENT requires — one enum for both sides, so the core
 *  predicate `userTier satisfies requiredTier` is directly expressible. Replaces
 *  `data.repository.EntitlementTier`. */
enum class AccessTier { FREE, PRO }

/** Whether and how a non-entitled user may obtain temporary access. Orthogonal to [AccessTier] —
 *  splitting these two axes is the entire point of this refactor. */
enum class AdUnlockPolicy {
    /** No ad path. The only route is an entitlement upgrade. */
    NONE,

    /** Repeatable, ephemeral, process-scoped. Never persisted (design §0/§4). */
    PER_USE,

    /** Exactly one ad-funded unlock, ever. Durably recorded so it can never repeat. */
    ONE_TIME_TRIAL,

    /** Durable but EXPIRING and RE-EARNABLE: after [ContentAccess.unlockWindowHours] elapse the
     *  content re-locks and the ad CTA is offered again (design D16). Deliberately payload-free --
     *  an enum constant is a singleton, so a duration on the constant would freeze one window into
     *  the type. Persisted to a SEPARATE store (`timed_ad_unlock` / `users/{uid}/timedUnlocks`),
     *  NEVER to the create-only `ad_unlock` / `users/{uid}/adUnlocks` store. */
    TIMED_REPEATABLE,
}

/** The content's declared requirement. Consumed identically by an affirmation group (this spec)
 *  and a meditation catalog entry (Spec 3). */
data class ContentAccess(
    val requiredTier: AccessTier,
    val adUnlock: AdUnlockPolicy = AdUnlockPolicy.NONE,
    /** Non-null IFF [adUnlock] is [AdUnlockPolicy.TIMED_REPEATABLE] -- enforced in `init`, so an
     *  unwindowed timed policy is unconstructible rather than a runtime surprise at grant time
     *  (design D16). */
    val unlockWindowHours: Int? = null,
) {
    init {
        require((unlockWindowHours != null) == (adUnlock == AdUnlockPolicy.TIMED_REPEATABLE)) {
            "unlockWindowHours must be non-null IFF adUnlock is TIMED_REPEATABLE"
        }
    }

    companion object {
        val Free = ContentAccess(AccessTier.FREE, AdUnlockPolicy.NONE)
        val Pro = ContentAccess(AccessTier.PRO, AdUnlockPolicy.NONE)
        val ProOrAdPerUse = ContentAccess(AccessTier.PRO, AdUnlockPolicy.PER_USE)
        val ProOrAdTrial = ContentAccess(AccessTier.PRO, AdUnlockPolicy.ONE_TIME_TRIAL)
        fun ProOrAdTimed(hours: Int) = ContentAccess(AccessTier.PRO, AdUnlockPolicy.TIMED_REPEATABLE, hours)
    }
}
