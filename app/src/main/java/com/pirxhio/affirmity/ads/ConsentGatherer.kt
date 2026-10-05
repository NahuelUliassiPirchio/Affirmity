package com.pirxhio.affirmity.ads

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private const val TAG = "ConsentGatherer"

/** Outcome of one UMP gather: [Completed] means the flow ran to its end (consent may still be
 *  denied -- callers check `canRequestAds()`); [Failed] carries the SDK error message. */
internal sealed interface ConsentGatherResult {
    data object Completed : ConsentGatherResult
    data class Failed(val reason: String) : ConsentGatherResult
}

/**
 * Thin SDK adapter running the UMP flow (`requestConsentInfoUpdate` +
 * `loadAndShowConsentFormIfRequired`). Shared by the launch-time gather in `MainActivity` and the
 * rewarded-ad path. The interstitial and banner stay passive-only: they never call this.
 *
 * The request is SINGLE-FLIGHT process-wide ([ConsentSingleFlight]): a caller that arrives while
 * one is in flight (a recreated Activity, a rewarded tap during the launch gather) awaits the same
 * result instead of starting a second request or form. The request runs in a process-scoped
 * coroutine, so cancelling a caller (Activity recreation cancels the launch effect) only cancels
 * its await. The request is started with the Activity of the FIRST caller; if that Activity is
 * recreated meanwhile, the SDK keeps it referenced until the form callback fires, a bounded,
 * SDK-owned temporary reference. Later callers still read `canRequestAds()` from their own Activity.
 *
 * ACCEPTED AS UNTESTED, same precedent as the other gateways ([UserMessagingPlatform] is static
 * and final). Its decision logic lives in [ConsentSingleFlight].
 */
internal object ConsentGatherer {

    private val singleFlight =
        ConsentSingleFlight(CoroutineScope(SupervisorJob() + Dispatchers.Main))

    /** Single-flight gather: joins a request already in flight, else starts one (always fresh
     *  after the previous one ended). Includes the debug-geography EEA settings when [isDebug] and
     *  [testDeviceHash] is set. */
    suspend fun gather(
        activity: Activity,
        testDeviceHash: String,
        isDebug: Boolean,
    ): ConsentGatherResult = singleFlight.run { request(activity, testDeviceHash, isDebug) }

    /** The raw SDK flow; runs on the main thread inside the process-scoped single-flight, which
     *  bounds it with a timeout. The `isActive` guards stay: after a timeout the continuation is
     *  cancelled and a late SDK callback must not resume it. */
    private suspend fun request(
        activity: Activity,
        testDeviceHash: String,
        isDebug: Boolean,
    ): ConsentGatherResult = suspendCancellableCoroutine { continuation ->
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val debugSettings = if (isDebug && testDeviceHash.isNotBlank()) {
            ConsentDebugSettings.Builder(activity)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .addTestDeviceHashedId(testDeviceHash)
                .build()
        } else {
            null
        }
        val paramsBuilder = ConsentRequestParameters.Builder()
        if (debugSettings != null) paramsBuilder.setConsentDebugSettings(debugSettings)
        val params = paramsBuilder.build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (!continuation.isActive) return@loadAndShowConsentFormIfRequired
                    if (formError != null) {
                        continuation.resume(ConsentGatherResult.Failed(formError.message))
                    } else {
                        continuation.resume(ConsentGatherResult.Completed)
                    }
                }
            },
            { updateError ->
                if (continuation.isActive) {
                    continuation.resume(ConsentGatherResult.Failed(updateError.message))
                }
            },
        )
    }

    /**
     * Launch-time, fire-and-forget consent. Joins any request in flight (including a rewarded
     * one) and does nothing once a request has completed successfully in this process (see
     * [ConsentSingleFlight.runOnceAfterSuccess]); a failure may be retried by a later launch.
     * Cancellation only stops waiting, never the request. Never throws (besides cancellation). When consent allows ads, initializes Mobile Ads and
     * invokes [onConsentAvailable] so the caller can preload. Call on the main thread.
     */
    suspend fun gatherAtLaunch(
        activity: Activity,
        testDeviceHash: String,
        isDebug: Boolean,
        onConsentAvailable: () -> Unit,
    ) {
        try {
            singleFlight.runOnceAfterSuccess { request(activity, testDeviceHash, isDebug) }
                ?: return
            val canRequestAds = UserMessagingPlatform.getConsentInformation(activity).canRequestAds()
            if (canRequestAds) {
                MobileAdsInitializer.ensureInitialized(activity.applicationContext)
                onConsentAvailable()
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Log.w(TAG, "launch consent flow failed: ${t.javaClass.simpleName}")
        }
    }
}
