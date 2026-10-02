package com.pirxhio.affirmity.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.UserMessagingPlatform
import com.pirxhio.affirmity.analytics.AdFailureReason
import com.pirxhio.affirmity.analytics.adLoadErrorCodeToReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Minimum gap between two load attempts, so a failing unit (no fill) is never retried in a loop. */
internal const val INTERSTITIAL_LOAD_THROTTLE_MS = 60_000L

private const val TAG = "RoundInterstitialAd"

/**
 * Thin SDK adapter for the round-end interstitial. All product decisions live in
 * [RoundInterstitialCoordinator]. Process-scoped singleton ([shared]): the loaded ad and the load
 * throttle survive Activity recreation, and no Composition-captured Context is ever held.
 *
 * ACCEPTED AS UNTESTED, same precedent as [GoogleRewardedAdGateway] and [BannerAdView]:
 * [InterstitialAd], [FullScreenContentCallback] and [UserMessagingPlatform] are final classes /
 * static factories, so a plain-JVM test cannot exercise this file. Its pure rules (expiry, error
 * code mapping) ARE tested.
 *
 * Consent is checked PASSIVELY via `canRequestAds()` -- this NEVER shows a UMP form mid-feed.
 */
internal class GoogleRoundInterstitialGateway private constructor(
    private val appContext: Context,
    private val adUnitId: String,
    private val activityProvider: () -> Activity?,
    private val onLoadFailed: (AdFailureReason) -> Unit,
    private val onShown: () -> Unit,
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() },
) : RoundInterstitialGateway {

    companion object {
        @Volatile private var instance: GoogleRoundInterstitialGateway? = null

        /** The first caller's [adUnitId]/[onLoadFailed] win; later calls (rotation) reuse the instance. */
        fun shared(
            context: Context,
            adUnitId: String,
            onLoadFailed: (AdFailureReason) -> Unit,
            onShown: () -> Unit,
        ): GoogleRoundInterstitialGateway = instance ?: synchronized(this) {
            instance ?: run {
                val app = context.applicationContext as Application
                ResumedActivityTracker.register(app)
                GoogleRoundInterstitialGateway(app, adUnitId, ResumedActivityTracker::resumed, onLoadFailed, onShown)
                    .also { instance = it }
            }
        }
    }

    private var ad: InterstitialAd? = null
    private var adLoadedAt = 0L
    private var loading = false
    private var lastLoadAttemptAt: Long? = null

    private fun consentAllows(): Boolean =
        UserMessagingPlatform.getConsentInformation(appContext).canRequestAds()

    override suspend fun preload() = withContext(Dispatchers.Main) {
        discardIfExpired()
        if (!canStartLoad()) return@withContext
        MobileAdsInitializer.ensureInitialized(appContext)
        loadNow()
    }

    private fun discardIfExpired() {
        if (ad != null && isInterstitialExpired(adLoadedAt, elapsedRealtime())) {
            Log.w(TAG, "discarding expired interstitial")
            ad = null
        }
    }

    private fun canStartLoad(): Boolean {
        if (ad != null || loading || adUnitId.isBlank() || !consentAllows()) return false
        val last = lastLoadAttemptAt
        return last == null || elapsedRealtime() - last >= INTERSTITIAL_LOAD_THROTTLE_MS
    }

    /** Main thread only. MobileAds must already be initialized (it is whenever an ad was loaded). */
    private fun loadNow() {
        if (!canStartLoad()) return
        lastLoadAttemptAt = elapsedRealtime()
        loading = true
        InterstitialAd.load(
            appContext,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(loaded: InterstitialAd) {
                    loading = false
                    ad = loaded
                    adLoadedAt = elapsedRealtime()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loading = false
                    ad = null
                    Log.w(TAG, "interstitial load failed, code=${error.code}")
                    onLoadFailed(adLoadErrorCodeToReason(error.code))
                }
            },
        )
    }

    override suspend fun show(): RoundInterstitialResult = withContext(Dispatchers.Main) {
        if (!consentAllows()) return@withContext RoundInterstitialResult.NoConsent
        discardIfExpired()
        val loaded = ad
        val activity = activityProvider()
        if (loaded == null || activity == null) {
            // Warm the next one for the following round; throttled, so never a retry loop.
            preload()
            return@withContext RoundInterstitialResult.NotReady
        }
        // An InterstitialAd is single-use: drop it now so it can never be shown twice.
        ad = null
        suspendCancellableCoroutine { continuation ->
            loaded.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    // Fires even if the caller was cancelled after show(): the ad is on screen, so the
                    // cooldown must still start.
                    onShown()
                    if (continuation.isActive) continuation.resume(RoundInterstitialResult.Shown)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    loaded.fullScreenContentCallback = null
                    Log.w(TAG, "interstitial show failed, code=${error.code}")
                    if (continuation.isActive) continuation.resume(RoundInterstitialResult.ShowFailed("show failed: ${error.message}"))
                    loadNow()
                }

                override fun onAdDismissedFullScreenContent() {
                    loaded.fullScreenContentCallback = null
                    Log.i(TAG, "interstitial dismissed; reloading next")
                    loadNow()
                }
            }
            // Only clear the callback if show() was never invoked; afterwards the Shown/dismiss
            // signals must still reach us (cooldown + reload).
            var showInvoked = false
            continuation.invokeOnCancellation { if (!showInvoked) loaded.fullScreenContentCallback = null }
            showInvoked = true
            loaded.show(activity)
        }
    }
}
