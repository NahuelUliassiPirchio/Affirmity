package com.pirxhio.affirmity.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.UserMessagingPlatform
import com.pirxhio.affirmity.access.AdPreparation
import com.pirxhio.affirmity.access.RewardedAdGateway
import com.pirxhio.affirmity.access.RewardedAdResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Straight-line adapter over the Mobile Ads SDK and UMP. Contains NO branching decision that
 * matters to the product: every guard, every policy->unit mapping and every outcome mapping lives
 * in [com.pirxhio.affirmity.access.RewardedAdUnlockSource] (design D2/D3) and is unit-tested there.
 *
 * ACCEPTED AS UNTESTED, EXPLICITLY. [RewardedAd], [RewardedAdLoadCallback],
 * [FullScreenContentCallback], [UserMessagingPlatform] are final classes
 * and static factories with no injectable construction, so a plain-JVM JUnit test cannot exercise
 * this file. This is the SAME precedent Specs 1-4 set for Compose routing code: the untestable
 * boundary is kept as thin as possible and everything above it is fully covered.
 */
internal class GoogleRewardedAdGateway(
    private val activityProvider: () -> Activity?,
    private val testDeviceHash: String,
    private val isDebug: Boolean,
) : RewardedAdGateway {

    private val initMutex = Mutex()
    @Volatile private var initialized = false

    override suspend fun prepare(): AdPreparation {
        val activity = activityProvider() ?: return AdPreparation.NoActivity
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        val consentResult = withContext(Dispatchers.Main) {
            ConsentGatherer.gather(activity, testDeviceHash, isDebug)
        }
        if (consentResult is ConsentGatherResult.Failed) {
            return AdPreparation.ConsentUnavailable(consentResult.reason)
        }

        if (!consentInformation.canRequestAds()) {
            return AdPreparation.ConsentUnavailable("canRequestAds() == false after consent flow")
        }

        initMutex.withLock {
            if (!initialized) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        MobileAds.initialize(activity) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    }
                }
                initialized = true
            }
        }
        return AdPreparation.Ready
    }

    override suspend fun loadAndShow(adUnitId: String): RewardedAdResult = withContext(Dispatchers.Main) {
        val loaded = load(adUnitId)
        if (loaded !is LoadOutcome.Loaded) return@withContext loaded.toResult()
        val activity = activityProvider() ?: return@withContext RewardedAdResult.NoActivity
        show(loaded.ad, activity)
    }

    private suspend fun load(adUnitId: String): LoadOutcome = suspendCancellableCoroutine { continuation ->
        val activity = activityProvider()
        if (activity == null) {
            continuation.resume(LoadOutcome.NoActivity)
            return@suspendCancellableCoroutine
        }
        RewardedAd.load(
            activity,
            adUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (continuation.isActive) continuation.resume(LoadOutcome.Loaded(ad))
                }

                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    if (continuation.isActive) {
                        continuation.resume(LoadOutcome.Failed(error.message))
                    }
                }
            },
        )
    }

    /** Resumes on a terminal full-screen callback, never on the reward callback -- this is what
     *  guarantees the app never resumes underneath a still-visible full-screen ad. */
    private suspend fun show(ad: RewardedAd, activity: Activity): RewardedAdResult =
        suspendCancellableCoroutine { continuation ->
            var earned = false
            var showFailure: String? = null

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (!continuation.isActive) return
                    val failure = showFailure
                    continuation.resume(
                        when {
                            failure != null -> RewardedAdResult.ShowFailed(failure)
                            earned -> RewardedAdResult.Rewarded
                            else -> RewardedAdResult.DismissedWithoutReward
                        },
                    )
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    showFailure = error.message
                    if (continuation.isActive) {
                        continuation.resume(RewardedAdResult.ShowFailed(error.message))
                    }
                }
            }

            ad.show(
                activity,
                OnUserEarnedRewardListener { _ -> earned = true },
            )
        }

    private sealed interface LoadOutcome {
        data class Loaded(val ad: RewardedAd) : LoadOutcome
        data object NoActivity : LoadOutcome
        data class Failed(val reason: String) : LoadOutcome

        fun toResult(): RewardedAdResult = when (this) {
            is Loaded -> error("Loaded must be handled before calling toResult()")
            NoActivity -> RewardedAdResult.NoActivity
            is Failed -> RewardedAdResult.LoadFailed(reason)
        }
    }
}

/** Walks the [ContextWrapper] chain to find the hosting [Activity], or `null` if none is
 *  reachable (e.g. an application [Context]) or the Activity is finishing/destroyed (EC-1). */
internal fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) {
            return if (context.isFinishing || context.isDestroyed) null else context
        }
        context = context.baseContext
    }
    return null
}
