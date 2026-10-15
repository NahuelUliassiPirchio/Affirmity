package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.access.AccessTier
import com.pirxhio.affirmity.access.AdUnlockRecord
import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.access.ContentKey
import com.pirxhio.affirmity.access.ContentType
import com.pirxhio.affirmity.data.repository.AdUnlockRepository
import com.pirxhio.affirmity.data.repository.Entitlement
import com.pirxhio.affirmity.data.repository.EntitlementRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CarouselAccessReaderTest {

    private val key = ContentKey(ContentType.AFFIRMATION_COLLECTION, "c1")
    private val record = AdUnlockRecord(key, grantedAtMillis = 1, expiresAtMillis = null)

    private class FakeEntitlements(private val flow: () -> Flow<Entitlement>) : EntitlementRepository {
        override fun observe(): Flow<Entitlement> = flow()
    }

    private class FakeAdUnlocks(
        private val durable: suspend () -> List<AdUnlockRecord> = { emptyList() },
        private val timed: () -> Flow<List<AdUnlockRecord>> = { flowOf(emptyList()) },
    ) : AdUnlockRepository {
        override fun observeDurableUnlocks(): Flow<List<AdUnlockRecord>> = flowOf(emptyList())
        override suspend fun getDurableUnlocks(): List<AdUnlockRecord> = durable()
        override suspend fun grantDurableUnlock(record: AdUnlockRecord) = Unit
        override fun observeTimedUnlocks(): Flow<List<AdUnlockRecord>> = timed()
        override suspend fun grantTimedUnlock(record: AdUnlockRecord) = Unit
    }

    private val pro = Entitlement(AccessTier.PRO)

    private fun reader(entitlements: EntitlementRepository, adUnlocks: AdUnlockRepository) =
        CarouselAccessReader(entitlements, adUnlocks, timeoutMillis = 4_000)

    @Test
    fun `reads tier and grants from the repositories`() = runTest {
        val access = reader(
            FakeEntitlements { flowOf(pro) },
            FakeAdUnlocks(durable = { listOf(record) }, timed = { flowOf(listOf(record)) }),
        ).read()

        assertEquals(AccessTier.PRO, access.tier)
        assertEquals(mapOf(key to record), access.grants.durableUnlocks)
        assertEquals(mapOf(key to record), access.grants.timedUnlocks)
        assertTrue(access.grants.sessionUnlocks.isEmpty())
    }

    @Test
    fun `signed-out style Free entitlement keeps the Room grants`() = runTest {
        val access = reader(
            FakeEntitlements { flowOf(Entitlement.Free) },
            FakeAdUnlocks(durable = { listOf(record) }),
        ).read()

        assertEquals(AccessTier.FREE, access.tier)
        assertEquals(mapOf(key to record), access.grants.durableUnlocks)
    }

    @Test
    fun `an entitlement flow that closes with an error fails closed`() = runTest {
        val access = reader(
            FakeEntitlements { callbackFlow<Entitlement> { close(IOException("boom")); awaitClose { } } },
            FakeAdUnlocks(durable = { listOf(record) }),
        ).read()

        assertEquals(CarouselAccess.FailClosed, access)
        assertEquals(AdUnlockState(), access.grants)
    }

    @Test
    fun `a throwing durable grants read fails closed even for a Pro user`() = runTest {
        val access = reader(
            FakeEntitlements { flowOf(pro) },
            FakeAdUnlocks(durable = { throw IllegalStateException("denied") }),
        ).read()

        assertEquals(CarouselAccess.FailClosed, access)
    }

    @Test
    fun `a timed grants flow that fails fails closed`() = runTest {
        val access = reader(
            FakeEntitlements { flowOf(pro) },
            FakeAdUnlocks(timed = { callbackFlow { close(IOException("boom")); awaitClose { } } }),
        ).read()

        assertEquals(CarouselAccess.FailClosed, access)
    }

    @Test
    fun `a hanging entitlement read fails closed after the timeout`() = runTest {
        val access = reader(
            FakeEntitlements { callbackFlow<Entitlement> { awaitClose { } } },
            FakeAdUnlocks(),
        ).read()

        assertEquals(CarouselAccess.FailClosed, access)
        assertEquals(4_000L, testScheduler.currentTime)
    }

    @Test
    fun `a hanging grants read fails closed after the timeout`() = runTest {
        val access = reader(
            FakeEntitlements { flowOf(pro) },
            FakeAdUnlocks(durable = { awaitCancellation() }),
        ).read()

        assertEquals(CarouselAccess.FailClosed, access)
    }

    @Test
    fun `an empty entitlement flow fails closed`() = runTest {
        val access = reader(FakeEntitlements { flowOf() }, FakeAdUnlocks()).read()

        assertEquals(CarouselAccess.FailClosed, access)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is never swallowed`() = runTest {
        reader(
            FakeEntitlements { flowOf(pro) },
            FakeAdUnlocks(durable = { throw CancellationException("cancelled") }),
        ).read()
    }

    // --- owned read ---

    @Test
    fun `owned ids are returned when the read succeeds`() = runTest {
        assertEquals(OwnedRead.Available(listOf("a")), readOwned(4_000) { listOf("a") })
    }

    @Test
    fun `owned read failure is Unavailable`() = runTest {
        assertEquals(OwnedRead.Unavailable, readOwned(4_000) { throw IOException("offline") })
    }

    @Test
    fun `owned read timeout is Unavailable`() = runTest {
        assertEquals(OwnedRead.Unavailable, readOwned(4_000) { awaitCancellation() })
        assertEquals(4_000L, testScheduler.currentTime)
    }
}
