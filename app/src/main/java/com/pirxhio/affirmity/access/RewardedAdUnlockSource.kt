package com.pirxhio.affirmity.access

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Real implementation of [AdUnlockSource] (Spec 5). Pure Kotlin -- no Compose, no direct SDK
 * import -- everything Android/SDK-facing lives behind [RewardedAdGateway] (REQ-5.1/5.3).
 *
 * Contains ALL branching logic: policy -> ad-unit selection, consent gating, outcome mapping,
 * single-flight guarding, timeout. [AdUnlockSource.kt] itself stays byte-for-byte unchanged
 * (REQ-4.1/5.2).
 */
class RewardedAdUnlockSource(
    private val gateway: RewardedAdGateway,
    private val adUnitIds: AdUnitIds,
    private val loadTimeoutMillis: Long = 30_000L,
) : AdUnlockSource {

    // App-wide single flight (REQ-4.8). tryLock, never suspend-and-queue: a second tap must be
    // IGNORED, not deferred into a second ad.
    private val inFlight = Mutex()

    override suspend fun requestUnlock(key: ContentKey, policy: AdUnlockPolicy): AdUnlockOutcome {
        val adUnitId = adUnitIdFor(policy) ?: return AdUnlockOutcome.Unavailable
        if (!inFlight.tryLock()) return AdUnlockOutcome.Unavailable
        return try {
            when (val prep = gateway.prepare()) {
                is AdPreparation.ConsentUnavailable, AdPreparation.NoActivity -> AdUnlockOutcome.Unavailable
                AdPreparation.Ready -> when (
                    val result = withTimeoutOrNull(loadTimeoutMillis) { gateway.loadAndShow(adUnitId) }
                ) {
                    RewardedAdResult.Rewarded -> AdUnlockOutcome.Earned
                    RewardedAdResult.DismissedWithoutReward -> AdUnlockOutcome.Dismissed
                    is RewardedAdResult.LoadFailed -> AdUnlockOutcome.Failed(result.reason)
                    is RewardedAdResult.ShowFailed -> AdUnlockOutcome.Failed(result.reason)
                    RewardedAdResult.NoActivity -> AdUnlockOutcome.Unavailable
                    null -> AdUnlockOutcome.Failed("timeout")
                }
            }
        } finally {
            inFlight.unlock()
        }
    }

    /** By policy, never by [ContentKey]/entry id (REQ-4.3) -- a future third `ProOrAdPerUse`
     *  content item requires no new ad unit and no code change here. */
    internal fun adUnitIdFor(policy: AdUnlockPolicy): String? = when (policy) {
        AdUnlockPolicy.PER_USE -> adUnitIds.perUse.ifBlank { null }
        AdUnlockPolicy.ONE_TIME_TRIAL -> adUnitIds.oneTimeTrial.ifBlank { null }
        AdUnlockPolicy.NONE -> null
    }
}
