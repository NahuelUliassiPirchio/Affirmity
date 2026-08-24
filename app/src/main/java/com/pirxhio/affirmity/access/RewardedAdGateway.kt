package com.pirxhio.affirmity.access

/**
 * The ONLY Android/SDK-touching dependency of [RewardedAdUnlockSource]. Its real implementation
 * (`ads.GoogleRewardedAdGateway`) is a straight-line adapter with ZERO branching, and is
 * ACCEPTED AS UNTESTED -- the same explicit precedent Specs 1-4 set for Compose routing. Every
 * decision, guard and mapping lives above this line, in pure Kotlin, and IS tested
 * ([RewardedAdUnlockSource]).
 */
interface RewardedAdGateway {
    /** Idempotent. Gathers UMP consent (form included) if required, then initializes MobileAds.
     *  Called lazily on the CTA tap -- never at app start. */
    suspend fun prepare(): AdPreparation

    /** Loads then immediately shows one rewarded ad. No preloading, no cache. */
    suspend fun loadAndShow(adUnitId: String): RewardedAdResult
}

sealed interface AdPreparation {
    data object Ready : AdPreparation

    /** Consent declined, form unavailable, `canRequestAds() == false`, or a UMP error. */
    data class ConsentUnavailable(val reason: String) : AdPreparation

    data object NoActivity : AdPreparation
}

sealed interface RewardedAdResult {
    data object Rewarded : RewardedAdResult
    data object DismissedWithoutReward : RewardedAdResult
    data class LoadFailed(val reason: String) : RewardedAdResult
    data class ShowFailed(val reason: String) : RewardedAdResult
    data object NoActivity : RewardedAdResult
}

/** Resolved from BuildConfig at the composition root. Blank = not configured. */
data class AdUnitIds(val perUse: String, val oneTimeTrial: String)
