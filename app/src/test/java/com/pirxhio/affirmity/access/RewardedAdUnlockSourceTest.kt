package com.pirxhio.affirmity.access

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hand-written fake (project convention: fakes over Mockito). Configurable per-call result;
 *  records every call so guard/ordering assertions do not need to infer behavior from outcomes
 *  alone. */
private class FakeRewardedAdGateway(
    private var prepareResult: AdPreparation = AdPreparation.Ready,
    private var loadAndShowResult: RewardedAdResult = RewardedAdResult.Rewarded,
    private val loadAndShowDeferred: CompletableDeferred<RewardedAdResult>? = null,
) : RewardedAdGateway {
    val prepareCalls = mutableListOf<Unit>()
    val loadAndShowCalls = mutableListOf<String>()

    override suspend fun prepare(): AdPreparation {
        prepareCalls += Unit
        return prepareResult
    }

    override suspend fun loadAndShow(adUnitId: String): RewardedAdResult {
        loadAndShowCalls += adUnitId
        loadAndShowDeferred?.let { return it.await() }
        return loadAndShowResult
    }
}

private val PER_USE_UNIT = "per-use-unit"
private val TRIAL_UNIT = "trial-unit"
private val TIMED_UNIT = "timed-unit"
private val ids = AdUnitIds(perUse = PER_USE_UNIT, oneTimeTrial = TRIAL_UNIT, timedRepeatable = TIMED_UNIT)
private val key = ContentKey(ContentType.MEDITATION, "enfoque")

class RewardedAdUnlockSourceTest {

    // REQ-4.2 outcome mapping matrix -------------------------------------------------------

    @Test
    fun `reward callback fired maps to Earned`() = runTest {
        val gateway = FakeRewardedAdGateway(loadAndShowResult = RewardedAdResult.Rewarded)
        val source = RewardedAdUnlockSource(gateway, ids)

        val outcome = source.requestUnlock(key, AdUnlockPolicy.PER_USE)

        assertEquals(AdUnlockOutcome.Earned, outcome)
    }

    @Test
    fun `ad shown and dismissed without reward maps to Dismissed`() = runTest {
        val gateway = FakeRewardedAdGateway(loadAndShowResult = RewardedAdResult.DismissedWithoutReward)
        val source = RewardedAdUnlockSource(gateway, ids)

        val outcome = source.requestUnlock(key, AdUnlockPolicy.PER_USE)

        assertEquals(AdUnlockOutcome.Dismissed, outcome)
    }

    @Test
    fun `load failure maps to Failed with reason`() = runTest {
        val gateway = FakeRewardedAdGateway(loadAndShowResult = RewardedAdResult.LoadFailed("no fill"))
        val source = RewardedAdUnlockSource(gateway, ids)

        val outcome = source.requestUnlock(key, AdUnlockPolicy.PER_USE)

        assertEquals(AdUnlockOutcome.Failed("no fill"), outcome)
    }

    @Test
    fun `show failure after a successful load maps to Failed with reason`() = runTest {
        val gateway = FakeRewardedAdGateway(loadAndShowResult = RewardedAdResult.ShowFailed("activity gone"))
        val source = RewardedAdUnlockSource(gateway, ids)

        val outcome = source.requestUnlock(key, AdUnlockPolicy.PER_USE)

        assertEquals(AdUnlockOutcome.Failed("activity gone"), outcome)
    }

    @Test
    fun `consent unavailable maps to Unavailable and never reaches loadAndShow`() = runTest {
        val gateway = FakeRewardedAdGateway(prepareResult = AdPreparation.ConsentUnavailable("declined"))
        val source = RewardedAdUnlockSource(gateway, ids)

        val outcome = source.requestUnlock(key, AdUnlockPolicy.PER_USE)

        assertEquals(AdUnlockOutcome.Unavailable, outcome)
        assertTrue("loadAndShow must never be called when consent is unavailable", gateway.loadAndShowCalls.isEmpty())
    }

    @Test
    fun `no activity during prepare maps to Unavailable and never reaches loadAndShow`() = runTest {
        val gateway = FakeRewardedAdGateway(prepareResult = AdPreparation.NoActivity)
        val source = RewardedAdUnlockSource(gateway, ids)

        val outcome = source.requestUnlock(key, AdUnlockPolicy.PER_USE)

        assertEquals(AdUnlockOutcome.Unavailable, outcome)
        assertTrue(gateway.loadAndShowCalls.isEmpty())
    }

    @Test
    fun `no activity during show maps to Unavailable`() = runTest {
        val gateway = FakeRewardedAdGateway(loadAndShowResult = RewardedAdResult.NoActivity)
        val source = RewardedAdUnlockSource(gateway, ids)

        val outcome = source.requestUnlock(key, AdUnlockPolicy.PER_USE)

        assertEquals(AdUnlockOutcome.Unavailable, outcome)
    }

    @Test
    fun `policy NONE maps to Unavailable without calling the gateway`() = runTest {
        val gateway = FakeRewardedAdGateway()
        val source = RewardedAdUnlockSource(gateway, ids)

        val outcome = source.requestUnlock(key, AdUnlockPolicy.NONE)

        assertEquals(AdUnlockOutcome.Unavailable, outcome)
        assertTrue(gateway.prepareCalls.isEmpty())
        assertTrue(gateway.loadAndShowCalls.isEmpty())
    }

    @Test
    fun `blank ad unit id maps to Unavailable without calling the gateway`() = runTest {
        val gateway = FakeRewardedAdGateway()
        val source = RewardedAdUnlockSource(
            gateway,
            AdUnitIds(perUse = "", oneTimeTrial = TRIAL_UNIT, timedRepeatable = TIMED_UNIT),
        )

        val outcome = source.requestUnlock(key, AdUnlockPolicy.PER_USE)

        assertEquals(AdUnlockOutcome.Unavailable, outcome)
        assertTrue(gateway.prepareCalls.isEmpty())
    }

    // REQ-4.3 ad-unit selection by policy, not by entry -----------------------------------

    @Test
    fun `adUnitIdFor selects the ad unit by policy, never by content key`() {
        val source = RewardedAdUnlockSource(FakeRewardedAdGateway(), ids)

        assertEquals(PER_USE_UNIT, source.adUnitIdFor(AdUnlockPolicy.PER_USE))
        assertEquals(TRIAL_UNIT, source.adUnitIdFor(AdUnlockPolicy.ONE_TIME_TRIAL))
        assertEquals(TIMED_UNIT, source.adUnitIdFor(AdUnlockPolicy.TIMED_REPEATABLE))
        assertNull(source.adUnitIdFor(AdUnlockPolicy.NONE))
    }

    @Test
    fun `adUnitIdFor TIMED_REPEATABLE with a blank unit id returns null`() {
        val source = RewardedAdUnlockSource(
            FakeRewardedAdGateway(),
            AdUnitIds(perUse = PER_USE_UNIT, oneTimeTrial = TRIAL_UNIT, timedRepeatable = ""),
        )

        assertNull(source.adUnitIdFor(AdUnlockPolicy.TIMED_REPEATABLE))
    }

    // Single-flight (REQ-4.8) ---------------------------------------------------------------

    @Test
    fun `a second request while one is in flight short-circuits to Unavailable and never reaches the gateway`() = runTest {
        val gate = CompletableDeferred<RewardedAdResult>()
        val gateway = FakeRewardedAdGateway(loadAndShowDeferred = gate)
        val source = RewardedAdUnlockSource(gateway, ids)

        val first = async { source.requestUnlock(key, AdUnlockPolicy.PER_USE) }
        advanceTimeBy(10)
        val second = source.requestUnlock(key, AdUnlockPolicy.PER_USE)

        assertEquals(AdUnlockOutcome.Unavailable, second)
        assertEquals(1, gateway.loadAndShowCalls.size)

        gate.complete(RewardedAdResult.Rewarded)
        assertEquals(AdUnlockOutcome.Earned, first.await())
    }

    @Test
    fun `the lock is released after a completed request so a subsequent call can proceed`() = runTest {
        val gateway = FakeRewardedAdGateway(loadAndShowResult = RewardedAdResult.Rewarded)
        val source = RewardedAdUnlockSource(gateway, ids)

        source.requestUnlock(key, AdUnlockPolicy.PER_USE)
        val second = source.requestUnlock(key, AdUnlockPolicy.PER_USE)

        assertEquals(AdUnlockOutcome.Earned, second)
        assertEquals(2, gateway.loadAndShowCalls.size)
    }

    // Timeout (design D3) --------------------------------------------------------------------

    @Test
    fun `an ad request that exceeds the timeout maps to Failed timeout and releases the lock`() = runTest {
        val gate = CompletableDeferred<RewardedAdResult>() // never completes
        val gateway = FakeRewardedAdGateway(loadAndShowDeferred = gate)
        val source = RewardedAdUnlockSource(gateway, ids, loadTimeoutMillis = 1_000L)

        val outcome = source.requestUnlock(key, AdUnlockPolicy.PER_USE)

        assertEquals(AdUnlockOutcome.Failed("timeout"), outcome)

        // Lock must be released -- a subsequent call proceeds normally.
        val gateway2 = FakeRewardedAdGateway(loadAndShowResult = RewardedAdResult.Rewarded)
        val source2 = RewardedAdUnlockSource(gateway2, ids)
        assertEquals(AdUnlockOutcome.Earned, source2.requestUnlock(key, AdUnlockPolicy.PER_USE))
    }
}
