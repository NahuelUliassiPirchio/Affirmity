package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockOutcome
import com.pirxhio.affirmity.ads.RoundInterstitialCoordinator
import com.pirxhio.affirmity.ads.RoundInterstitialGateway
import com.pirxhio.affirmity.ads.RoundInterstitialResult
import com.pirxhio.affirmity.ads.RoundInterstitialStore
import com.pirxhio.affirmity.analytics.FakeAnalyticsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

private class MemoryStore : RoundInterstitialStore {
    var last: Long? = null
    override suspend fun lastShownAtMillis(): Long? = last
    override suspend fun saveLastShownAtMillis(millis: Long) { last = millis }
}

class AffirmityAppStateRoundInterstitialTest {

    private class Fixture(enabled: Boolean = true, withCoordinator: Boolean = true) {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val gateway = CountingGateway()
        val state = buildAnalyticsState(
            scope,
            FakeAnalyticsLogger(),
            object : com.pirxhio.affirmity.access.AdUnlockSource {
                override suspend fun requestUnlock(
                    key: com.pirxhio.affirmity.access.ContentKey,
                    policy: com.pirxhio.affirmity.access.AdUnlockPolicy,
                ) = AdUnlockOutcome.Earned
            },
            roundInterstitial = if (withCoordinator) {
                RoundInterstitialCoordinator(gateway, MemoryStore(), FakeAnalyticsLogger(), { 1_000_000L }, enabled)
            } else {
                null
            },
        )
    }

    @Test
    fun `without a coordinator both entry points are no-ops`() = runBlocking {
        val f = Fixture(withCoordinator = false)
        f.state.onFeedRoundCompleted(12)
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
        f.state.onFeedRoundCompleted(12)
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
        f.state.onFeedRoundCompleted(12)
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
        f.state.onFeedRoundCompleted(12)
        delay(50)
        assertEquals(1, f.gateway.preloads)
        assertEquals(1, f.gateway.shows)
        f.scope.cancel()
    }

    @Test
    fun `kill switch off never preloads or shows`() = runBlocking {
        val f = Fixture(enabled = false)
        delay(50)
        f.state.entitlementTier.value = AccessTier.FREE
        f.state.entitlementResolved.value = true
        f.state.preloadRoundInterstitial()
        f.state.onFeedRoundCompleted(12)
        delay(50)
        assertEquals(0, f.gateway.shows + f.gateway.preloads)
        f.scope.cancel()
    }
}
