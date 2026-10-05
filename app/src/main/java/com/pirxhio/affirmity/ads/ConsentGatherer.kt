package com.pirxhio.affirmity.ads

import android.app.Activity
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
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
 * Pure once-per-process decision for the launch-time consent flow: at most one run in flight, and
 * no re-run after a successful one (recreation must not re-request). A failed run may be retried
 * by a later launch. Kept free of SDK types so it is unit-tested.
 */
internal class ConsentLaunchGuard {
    private var running = false
    private var done = false

    @Synchronized
    fun tryStart(): Boolean {
        if (running || done) return false
        running = true
        return true
    }

    @Synchronized
    fun finish(succeeded: Boolean) {
        running = false
        if (succeeded) done = true
    }
}

/**
 * Thin SDK adapter running the UMP flow (`requestConsentInfoUpdate` +
 * `loadAndShowConsentFormIfRequired`). Shared by the launch-time gather in `MainActivity` and the
 * rewarded-ad path. The interstitial and banner stay passive-only: they never call this.
 *
 * ACCEPTED AS UNTESTED, same precedent as the other gateways ([UserMessagingPlatform] is static
 * and final). Its decision logic lives in [ConsentLaunchGuard].
 */
internal object ConsentGatherer {

    private val launchGuard = ConsentLaunchGuard()

    /** Must be called on the main thread. Includes the debug-geography EEA settings when
     *  [isDebug] and [testDeviceHash] is set. */
    suspend fun gather(
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
     * Launch-time, fire-and-forget consent. Runs at most once per process (see
     * [ConsentLaunchGuard]); never throws. When consent allows ads, initializes Mobile Ads and
     * invokes [onConsentAvailable] so the caller can preload. Call on the main thread.
     */
    suspend fun gatherAtLaunch(
        activity: Activity,
        testDeviceHash: String,
        isDebug: Boolean,
        onConsentAvailable: () -> Unit,
    ) {
        if (!launchGuard.tryStart()) return
        var succeeded = false
        try {
            val result = gather(activity, testDeviceHash, isDebug)
            succeeded = result is ConsentGatherResult.Completed
            val canRequestAds = UserMessagingPlatform.getConsentInformation(activity).canRequestAds()
            if (canRequestAds) {
                MobileAdsInitializer.ensureInitialized(activity.applicationContext)
                onConsentAvailable()
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Log.w(TAG, "launch consent flow failed: ${t.javaClass.simpleName}")
        } finally {
            launchGuard.finish(succeeded)
        }
    }
}
