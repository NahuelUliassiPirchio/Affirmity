package com.pirxhio.affirmity.access

/**
 * The single decision point Specs 3, 4 and 5 all call. Pure, total, Android-free, and — critically
 * — **never creates a grant**. Resolution is read-only, so the proposal's "rewarded ads are always
 * opt-in" constraint is structural, not a convention a future caller can forget.
 *
 * `nowMillis` is passed rather than read, mirroring `EntitlementResolution.resolveTier`.
 */
fun resolveAccess(
    key: ContentKey,
    content: ContentAccess,
    userTier: AccessTier,
    grants: AdUnlockState,
    nowMillis: Long,
): AccessDecision {
    // 1. Entitlement wins outright. A PRO user never consults grants, so a PRO user can never
    //    acquire or depend on one — the property Q4 leans on.
    if (userTier == AccessTier.PRO || content.requiredTier == AccessTier.FREE) {
        return AccessDecision.Unlocked
    }

    // 2. Not entitled. The CONTENT's current policy decides whether grants are even consulted, so
    //    flipping a group's policy to NONE immediately stops honoring stale grants.
    return when (content.adUnlock) {
        AdUnlockPolicy.NONE -> AccessDecision.LockedNeedsPro

        AdUnlockPolicy.PER_USE ->
            if (key in grants.sessionUnlocks) AccessDecision.UnlockedByAd(AdUnlockPolicy.PER_USE)
            else AccessDecision.LockedAdUnlockable(AdUnlockPolicy.PER_USE)

        AdUnlockPolicy.ONE_TIME_TRIAL -> {
            val record = grants.durableUnlocks[key]
            when {
                record == null -> AccessDecision.LockedAdUnlockable(AdUnlockPolicy.ONE_TIME_TRIAL)
                !record.hasExpired(nowMillis) -> AccessDecision.UnlockedByAd(AdUnlockPolicy.ONE_TIME_TRIAL)
                // Spent: the record's existence is the non-repeatability proof.
                else -> AccessDecision.LockedNeedsPro
            }
        }
    }
}
