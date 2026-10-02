package com.pirxhio.affirmity.ads

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.analytics.AdFailureReason
import com.pirxhio.affirmity.analytics.AnalyticsEvent
import com.pirxhio.affirmity.analytics.FakeAnalyticsLogger
import com.pirxhio.affirmity.analytics.FeedSizeBucket
import com.pirxhio.affirmity.analytics.RoundSkipReason
import com.pirxhio.affirmity.ui.affirmations.ROUND_INTERSTITIAL_COOLDOWN_MS
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeGateway(var result: RoundInterstitialResult = RoundInterstitialResult.Shown) : RoundInterstitialGateway {
    var showCalls = 0
    var preloadCalls = 0
    var throwOnShow = false

    override suspend fun preload() {
        preloadCalls++
    }

    override suspend fun show(): RoundInterstitialResult {
        showCalls++
        if (throwOnShow) error("boom")
        return result
    }
}

private class FakeStore(var last: Long? = null) : RoundInterstitialStore {
    var throwOnRead = false
    var throwOnWrite = false

    override suspend fun lastShownAtMillis(): Long? {
        if (throwOnRead) error("read boom")
        return last
    }

    override suspend fun saveLastShownAtMillis(millis: Long) {
        if (throwOnWrite) error("write boom")
        last = millis
    }
}

class RoundInterstitialCoordinatorTest {

    private var now = 50_000_000L
    private val gateway = FakeGateway()
    private val store = FakeStore()
    private val analytics = FakeAnalyticsLogger()
    private val coordinator = RoundInterstitialCoordinator(gateway, store, analytics, nowMillis = { now })

    @Test
    fun `free user at round end sees the interstitial once and the timestamp is stored`() = runBlocking {
        coordinator.onRoundCompleted(AccessTier.FREE, feedSize = 12)

        assertEquals(1, gateway.showCalls)
        assertEquals(now, store.last)
        assertTrue(analytics.recorded.contains(AnalyticsEvent.RoundCompleted(FeedSizeBucket.SIZE_10_24)))
        assertTrue(analytics.recorded.contains(AnalyticsEvent.RoundInterstitialShown))
    }

    @Test
    fun `pro never reaches the gateway`() = runBlocking {
        coordinator.onRoundCompleted(AccessTier.PRO, feedSize = 12)

        assertEquals(0, gateway.showCalls)
        assertNull(store.last)
        assertTrue(analytics.recorded.contains(AnalyticsEvent.RoundInterstitialSkipped(RoundSkipReason.PREMIUM)))
    }

    @Test
    fun `second round inside the cooldown is skipped and after it is shown`() = runBlocking {
        coordinator.onRoundCompleted(AccessTier.FREE, 12)
        now += ROUND_INTERSTITIAL_COOLDOWN_MS - 1_000L
        coordinator.onRoundCompleted(AccessTier.FREE, 12)
        assertEquals(1, gateway.showCalls)
        assertTrue(analytics.recorded.contains(AnalyticsEvent.RoundInterstitialSkipped(RoundSkipReason.COOLDOWN)))

        now += 1_000L
        coordinator.onRoundCompleted(AccessTier.FREE, 12)
        assertEquals(2, gateway.showCalls)
    }

    @Test
    fun `no consent is silent and does not start the cooldown`() = runBlocking {
        gateway.result = RoundInterstitialResult.NoConsent
        coordinator.onRoundCompleted(AccessTier.FREE, 12)

        assertNull(store.last)
        assertTrue(analytics.recorded.contains(AnalyticsEvent.RoundInterstitialSkipped(RoundSkipReason.NO_CONSENT)))
    }

    @Test
    fun `an ad that is not loaded is silent and does not start the cooldown`() = runBlocking {
        gateway.result = RoundInterstitialResult.NotReady
        coordinator.onRoundCompleted(AccessTier.FREE, 12)

        assertNull(store.last)
        assertTrue(analytics.recorded.contains(AnalyticsEvent.RoundInterstitialSkipped(RoundSkipReason.NOT_LOADED)))
    }

    @Test
    fun `a show failure is reported with a bounded reason and starts no cooldown`() = runBlocking {
        gateway.result = RoundInterstitialResult.ShowFailed("Ad failed to show")
        coordinator.onRoundCompleted(AccessTier.FREE, 12)

        assertNull(store.last)
        assertTrue(analytics.recorded.contains(AnalyticsEvent.RoundInterstitialFailed(AdFailureReason.SHOW_FAILED)))
    }

    @Test
    fun `an exception inside the gateway never escapes`() = runBlocking {
        gateway.throwOnShow = true
        coordinator.onRoundCompleted(AccessTier.FREE, 12)

        assertNull(store.last)
    }

    @Test
    fun `preload is skipped for pro and requested for free`() = runBlocking {
        coordinator.preload(AccessTier.PRO)
        assertEquals(0, gateway.preloadCalls)
        coordinator.preload(AccessTier.FREE)
        assertEquals(1, gateway.preloadCalls)
    }

    @Test
    fun `debug trigger bypasses policy but still goes through the gateway`() = runBlocking {
        store.last = now
        coordinator.triggerForDebug()

        assertEquals(1, gateway.showCalls)
    }

    @Test
    fun `unresolved entitlement drops the round and never touches the gateway`() = runBlocking {
        coordinator.preload(null)
        coordinator.onRoundCompleted(null, 12)

        assertEquals(0, gateway.preloadCalls)
        assertEquals(0, gateway.showCalls)
        assertTrue(analytics.recorded.isEmpty())
    }

    @Test
    fun `kill switch off means no preload no show and no analytics`() = runBlocking {
        val off = RoundInterstitialCoordinator(gateway, store, analytics, { now }, enabled = false)
        off.preload(AccessTier.FREE)
        off.onRoundCompleted(AccessTier.FREE, 12)
        off.triggerForDebug()

        assertEquals(0, gateway.preloadCalls)
        assertEquals(0, gateway.showCalls)
        assertTrue(analytics.recorded.isEmpty())
    }

    @Test
    fun `a failing cooldown read skips the show`() = runBlocking {
        store.throwOnRead = true
        coordinator.onRoundCompleted(AccessTier.FREE, 12)

        assertEquals(0, gateway.showCalls)
    }

    @Test
    fun `a failing cooldown write cannot cause ad spam`() = runBlocking {
        store.throwOnWrite = true
        coordinator.onRoundCompleted(AccessTier.FREE, 12)
        coordinator.onRoundCompleted(AccessTier.FREE, 12)

        assertEquals(1, gateway.showCalls)
        assertTrue(analytics.recorded.contains(AnalyticsEvent.RoundInterstitialSkipped(RoundSkipReason.COOLDOWN)))
    }

    @Test
    fun `cooldown survives a rebuilt coordinator when the persisted write failed`() = runBlocking {
        val shared = LastShownMemory()
        store.throwOnWrite = true
        RoundInterstitialCoordinator(gateway, store, analytics, nowMillis = { now }, lastShown = shared)
            .onRoundCompleted(AccessTier.FREE, 12)
        // Activity recreation: brand new coordinator, same process-level memory, nothing persisted.
        RoundInterstitialCoordinator(gateway, store, analytics, nowMillis = { now }, lastShown = shared)
            .onRoundCompleted(AccessTier.FREE, 12)

        assertEquals(1, gateway.showCalls)
    }

    @Test
    fun `a gateway that never calls back times out and does not block later rounds`() = runBlocking {
        val hanging = object : RoundInterstitialGateway {
            var shows = 0
            override suspend fun preload() = Unit
            override suspend fun show(): RoundInterstitialResult {
                shows++
                kotlinx.coroutines.awaitCancellation()
            }
        }
        val c = RoundInterstitialCoordinator(hanging, store, analytics, nowMillis = { now }, showTimeoutMs = 100L)
        c.onRoundCompleted(AccessTier.FREE, 12)
        c.onRoundCompleted(AccessTier.FREE, 12)

        assertEquals(2, hanging.shows)
        assertTrue(analytics.recorded.contains(AnalyticsEvent.RoundInterstitialSkipped(RoundSkipReason.NOT_LOADED)))
    }

    @Test
    fun `expiry boundary is fifty minutes`() {
        assertEquals(50 * 60 * 1000L, INTERSTITIAL_MAX_AGE_MS)
        assertEquals(false, isInterstitialExpired(loadedAt = 0L, now = INTERSTITIAL_MAX_AGE_MS))
        assertEquals(true, isInterstitialExpired(loadedAt = 0L, now = INTERSTITIAL_MAX_AGE_MS + 1))
    }

    @Test
    fun `feed size buckets are bounded`() {
        assertEquals(FeedSizeBucket.SIZE_10_24, feedSizeBucket(10))
        assertEquals(FeedSizeBucket.SIZE_10_24, feedSizeBucket(24))
        assertEquals(FeedSizeBucket.SIZE_25_49, feedSizeBucket(25))
        assertEquals(FeedSizeBucket.SIZE_50_99, feedSizeBucket(99))
        assertEquals(FeedSizeBucket.SIZE_100_PLUS, feedSizeBucket(100))
    }
}
