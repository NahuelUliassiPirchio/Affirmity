package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockOutcome
import com.pirxhio.affirmity.ads.RoundInterstitialCoordinator
import com.pirxhio.affirmity.ads.RoundInterstitialGateway
import com.pirxhio.affirmity.ads.RoundInterstitialResult
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.FakeAnalyticsLogger
import com.pirxhio.affirmity.analytics.FeedSizeBucket
import com.pirxhio.affirmity.analytics.RoundSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class CountingGateway : RoundInterstitialGateway {
    var preloads = 0
    var shows = 0
    override suspend fun preload() { preloads++ }
    override suspend fun show(): RoundInterstitialResult {
        shows++
        return RoundInterstitialResult.Shown
    }
}

class AffirmityAppStateRoundInterstitialTest {

    private class Fixture(enabled: Boolean = true, withCoordinator: Boolean = true) {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val gateway = CountingGateway()
        val analytics = FakeAnalyticsLogger()
        val state = buildAnalyticsState(
            scope,
            analytics,
            object : com.pirxhio.affirmity.access.AdUnlockSource {
                override suspend fun requestUnlock(
                    key: com.pirxhio.affirmity.access.ContentKey,
                    policy: com.pirxhio.affirmity.access.AdUnlockPolicy,
                ) = AdUnlockOutcome.Earned
            },
            roundInterstitial = if (withCoordinator) {
                RoundInterstitialCoordinator(gateway, analytics, enabled)
            } else {
                null
            },
        )
    }

    @Test
    fun `without a coordinator both entry points are no-ops`() = runBlocking {
        val f = Fixture(withCoordinator = false)
        f.state.onRoundCompleted(12, RoundSource.FEED)
        f.state.preloadRoundInterstitial()
        delay(50)
        assertEquals(0, f.gateway.shows + f.gateway.preloads)
        f.scope.cancel()
    }

    @Test
    fun `unresolved entitlement never preloads or shows`() = runBlocking {
        val f = Fixture()
        delay(50)
        f.state.entitlementResolved.value = false
        f.state.onRoundCompleted(12, RoundSource.FEED)
        f.state.preloadRoundInterstitial()
        delay(50)
        assertEquals(0, f.gateway.shows + f.gateway.preloads)
        f.scope.cancel()
    }

    @Test
    fun `resolved pro never preloads or shows`() = runBlocking {
        val f = Fixture()
        delay(50)
        f.state.entitlementTier.value = AccessTier.PRO
        f.state.entitlementResolved.value = true
        f.state.onRoundCompleted(12, RoundSource.FEED)
        f.state.preloadRoundInterstitial()
        delay(50)
        assertEquals(0, f.gateway.shows + f.gateway.preloads)
        f.scope.cancel()
    }

    @Test
    fun `resolved free preloads then shows`() = runBlocking {
        val f = Fixture()
        delay(50)
        f.state.entitlementTier.value = AccessTier.FREE
        f.state.entitlementResolved.value = true
        f.state.preloadRoundInterstitial()
        f.state.onRoundCompleted(12, RoundSource.FEED)
        delay(50)
        assertEquals(1, f.gateway.preloads)
        assertEquals(1, f.gateway.shows)
        f.scope.cancel()
    }

    @Test
    fun `the round source is forwarded to the analytics event`() = runBlocking {
        val f = Fixture()
        delay(50)
        f.state.entitlementTier.value = AccessTier.FREE
        f.state.entitlementResolved.value = true
        f.state.onRoundCompleted(12, RoundSource.GROUP)
        delay(50)
        assertTrue(f.analytics.recorded.contains(AnalyticsEvent.RoundCompleted(FeedSizeBucket.SIZE_10_24, RoundSource.GROUP)))
        f.scope.cancel()
    }

    @Test
    fun `kill switch off never preloads or shows`() = runBlocking {
        val f = Fixture(enabled = false)
        delay(50)
        f.state.entitlementTier.value = AccessTier.FREE
        f.state.entitlementResolved.value = true
        f.state.preloadRoundInterstitial()
        f.state.onRoundCompleted(12, RoundSource.FEED)
        delay(50)
        assertEquals(0, f.gateway.shows + f.gateway.preloads)
        f.scope.cancel()
    }
}
