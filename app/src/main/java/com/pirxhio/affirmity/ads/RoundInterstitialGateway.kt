package com.pirxhio.affirmity.ads

/**
 * Test seam over the real interstitial SDK adapter ([GoogleRoundInterstitialGateway]). Every
 * product decision lives above it in [RoundInterstitialCoordinator]; implementations must never
 * throw to the caller, never show UI other than the ad itself, and never open a consent form.
 */
interface RoundInterstitialGateway {
    /** Best-effort, throttled preload of the next ad. Silent on any failure. */
    suspend fun preload()

    /** Shows the preloaded ad if (and only if) it is ready, consent allows and the app is foregrounded. */
    suspend fun show(): RoundInterstitialResult
}

sealed interface RoundInterstitialResult {
    data object Shown : RoundInterstitialResult
    data object NoConsent : RoundInterstitialResult

    /** Not loaded yet, no resumed Activity, or the app is backgrounded. */
    data object NotReady : RoundInterstitialResult
    data class ShowFailed(val reason: String) : RoundInterstitialResult
}

/** A preloaded interstitial older than this is discarded (AdMob invalidates cached ads after ~1h). */
const val INTERSTITIAL_MAX_AGE_MS = 50 * 60 * 1000L

fun isInterstitialExpired(loadedAt: Long, now: Long): Boolean = now - loadedAt > INTERSTITIAL_MAX_AGE_MS

/** Minimum gap between two load attempts, so a failing unit (no fill) is never retried in a loop. */
internal const val INTERSTITIAL_LOAD_THROTTLE_MS = 60_000L

/**
 * Whether a new load must wait. [afterDismiss] bypasses the window: a dismissal proves the unit
 * just filled and showed, so reloading for the next round cannot become a no-fill retry loop, and
 * waiting would leave a fast swiper's next completed round without an ad.
 */
internal fun isInterstitialLoadThrottled(lastAttemptAt: Long?, now: Long, afterDismiss: Boolean): Boolean =
    !afterDismiss && lastAttemptAt != null && now - lastAttemptAt < INTERSTITIAL_LOAD_THROTTLE_MS
