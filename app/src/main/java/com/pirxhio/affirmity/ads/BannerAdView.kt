package com.pirxhio.affirmity.ads

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.delay

/** Design's midpoint of the proposal's 2-3s entry-request window. */
internal const val MEDITATION_BANNER_REQUEST_DELAY_MS = 2_500L

private enum class BannerPhase { Pending, Loaded, Failed }

/**
 * Independent AdMob banner wrapper for the free-tier guided meditation screen (design D2/D4/D6).
 *
 * ACCEPTED AS UNTESTED, EXPLICITLY, same precedent as [GoogleRewardedAdGateway]: [AdView],
 * [AdListener] and [UserMessagingPlatform] are final classes / static factories with no
 * injectable construction, so a plain-JVM JUnit test cannot exercise this file.
 *
 * Consent is checked passively via [UserMessagingPlatform.getConsentInformation] — this NEVER
 * triggers a UMP consent form (design D5, resolved tradeoff): a banner is a passive placement,
 * and popping a consent sheet mid-meditation would be hostile. A user who never gathered consent
 * elsewhere (e.g. the rewarded-ad CTA) silently never sees this banner either.
 */
@Composable
fun BannerAdView(
    adUnitId: String,
    modifier: Modifier = Modifier,
    requestDelayMillis: Long = MEDITATION_BANNER_REQUEST_DELAY_MS,
) {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val activity = remember(context) { context.findActivity() }
    var phase by remember { mutableStateOf(BannerPhase.Pending) }

    val adView = remember(activity) {
        activity?.let { hostActivity ->
            AdView(hostActivity).apply {
                setAdUnitId(adUnitId)
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(hostActivity, screenWidthDp))
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        phase = BannerPhase.Loaded
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        phase = BannerPhase.Failed
                    }
                }
            }
        }
    }

    // Single delay+load site — the fixed, never-remounted banner slot (D2) guarantees this runs
    // exactly once per screen entry.
    LaunchedEffect(adView) {
        val view = adView ?: return@LaunchedEffect
        delay(requestDelayMillis)
        if (!UserMessagingPlatform.getConsentInformation(activity!!).canRequestAds()) {
            phase = BannerPhase.Failed
            return@LaunchedEffect
        }
        MobileAdsInitializer.ensureInitialized(activity)
        view.loadAd(AdRequest.Builder().build())
    }

    // Declared outside the phase conditional so it still fires on Failed's un-emit (D4).
    DisposableEffect(adView) {
        onDispose { adView?.destroy() }
    }

    when (phase) {
        BannerPhase.Failed -> Unit
        else -> AndroidView(
            factory = { adView!! },
            modifier = modifier.then(if (phase == BannerPhase.Loaded) Modifier else Modifier.height(0.dp)),
        )
    }
}
