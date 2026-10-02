package com.pirxhio.affirmity.ads

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.AnalyticsLogger
import com.pirxhio.affirmity.analytics.FeedSizeBucket
import com.pirxhio.affirmity.analytics.RoundSkipReason
import com.pirxhio.affirmity.analytics.toAdFailureReason
import com.pirxhio.affirmity.ui.affirmations.RoundInterstitialDecision
import com.pirxhio.affirmity.ui.affirmations.decideRoundInterstitial
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal fun feedSizeBucket(size: Int): FeedSizeBucket = when {
    size < 25 -> FeedSizeBucket.SIZE_10_24
    size < 50 -> FeedSizeBucket.SIZE_25_49
    size < 100 -> FeedSizeBucket.SIZE_50_99
    else -> FeedSizeBucket.SIZE_100_PLUS
}

private const val TAG = "RoundInterstitial"

/** A show() that has not reported back after this long is abandoned so later rounds are not queued forever. */
const val ROUND_INTERSTITIAL_SHOW_TIMEOUT_MS = 30_000L

/**
 * In-memory last-shown fallback. The composition root passes ONE process-level instance to every
 * coordinator (and to the gateway's shown hook), so it outlives Activity recreation.
 */
class LastShownMemory {
    @Volatile var value: Long? = null
}

/**
 * Pure orchestration of the round-end interstitial: policy -> gateway -> bookkeeping -> analytics.
 * The feed is never blocked: every outcome other than "shown" is silent, and nothing thrown by the
 * gateway or the store escapes (only cancellation propagates; failures are logged, never PII).
 *
 * [tier] is null until the entitlement has resolved: a null tier means no preload, no show, and the
 * completed round is DROPPED (not deferred) -- the next round is evaluated with a resolved tier.
 * [enabled] is the kill switch: when false, every entry point is a silent no-op.
 */
class RoundInterstitialCoordinator(
    private val gateway: RoundInterstitialGateway,
    private val store: RoundInterstitialStore,
    private val analytics: AnalyticsLogger,
    private val nowMillis: () -> Long,
    private val enabled: Boolean = true,
    /** Process-lifetime fallback so a failing store write can never cause back-to-back ads. */
    private val lastShown: LastShownMemory = LastShownMemory(),
    private val showTimeoutMs: Long = ROUND_INTERSTITIAL_SHOW_TIMEOUT_MS,
) {
    private val mutex = Mutex()

    suspend fun onRoundCompleted(tier: AccessTier?, feedSize: Int) = guarded {
        if (!enabled || tier == null) return@guarded
        mutex.withLock {
            analytics.log(AnalyticsEvent.RoundCompleted(feedSizeBucket(feedSize)))
            val now = nowMillis()
            val persisted = try {
                store.lastShownAtMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Unknown cooldown state: safest is to not show.
                Log.w(TAG, "cooldown read failed; skipping interstitial", e)
                return@withLock
            }
            val last = listOfNotNull(persisted, lastShown.value).maxOrNull()
            when (decideRoundInterstitial(tier, last, now)) {
                RoundInterstitialDecision.SKIP_PREMIUM -> skipped(RoundSkipReason.PREMIUM)
                RoundInterstitialDecision.SKIP_COOLDOWN -> skipped(RoundSkipReason.COOLDOWN)
                RoundInterstitialDecision.SHOW -> showAndRecord(now)
            }
        }
    }

    /** Warms the next ad. Pro users never see one, so they never pay for the load either. */
    suspend fun preload(tier: AccessTier?) = guarded {
        if (!enabled || tier == null || tier == AccessTier.PRO) return@guarded
        gateway.preload()
    }

    /** Debug-only (no production caller): skips tier and cooldown, still honors consent/readiness. */
    suspend fun triggerForDebug() = guarded {
        if (!enabled) return@guarded
        gateway.show()
    }

    private suspend fun showAndRecord(now: Long) {
        val result = withTimeoutOrNull(showTimeoutMs) { gateway.show() }
        if (result == null) {
            Log.w(TAG, "interstitial show timed out after ${showTimeoutMs}ms; abandoning")
            skipped(RoundSkipReason.NOT_LOADED)
            return
        }
        when (result) {
            RoundInterstitialResult.Shown -> {
                lastShown.value = now
                try {
                    store.saveLastShownAtMillis(now)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "cooldown write failed; in-memory cooldown still applies", e)
                }
                analytics.log(AnalyticsEvent.RoundInterstitialShown)
            }
            RoundInterstitialResult.NoConsent -> skipped(RoundSkipReason.NO_CONSENT)
            RoundInterstitialResult.NotReady -> skipped(RoundSkipReason.NOT_LOADED)
            is RoundInterstitialResult.ShowFailed -> {
                Log.w(TAG, "interstitial show failed: ${result.reason}")
                analytics.log(AnalyticsEvent.RoundInterstitialFailed(result.reason.toAdFailureReason()))
            }
        }
    }

    private fun skipped(reason: RoundSkipReason) {
        analytics.log(AnalyticsEvent.RoundInterstitialSkipped(reason))
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            // An ad must never break or delay the feed; keep the evidence in logcat.
            Log.w(TAG, "round interstitial step failed", e)
        }
    }
}
