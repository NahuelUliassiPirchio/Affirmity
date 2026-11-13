package com.pirxhio.affirmity.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogFeedGateTest {
    private fun feedReady(gate: CatalogFeedGate, ownedEmitted: Boolean = true) = isFeedContentReady(
        hasCatalogRows = false, hasOwnedRows = false,
        catalogEmitted = gate.observedAfterSeed, ownedEmitted = ownedEmitted,
        catalogSeedSettled = gate.seedSettled,
    )

    @Test fun `an empty emission held from before the seed settled is not a resolved-empty feed`() {
        val gate = CatalogFeedGate()
        gate.onEmission()          // empty snapshot observed while the seed is still running
        gate.onSeedSettled()       // seed finishes; the collector has not seen the new rows yet
        assertFalse(feedReady(gate))
    }

    @Test fun `an emission observed after the seed settled resolves the feed`() {
        val gate = CatalogFeedGate()
        gate.onEmission()
        gate.onSeedSettled()
        gate.onEmission()
        assertTrue(feedReady(gate))
    }

    @Test fun `an explicitly confirmed empty read resolves a feed that never re-emits`() {
        val gate = CatalogFeedGate()
        gate.onEmission()
        gate.onSeedSettled()
        assertFalse(feedReady(gate))
        gate.onConfirmedEmpty()
        assertTrue(feedReady(gate))
    }

    @Test fun `a gate built with no seeder is settled from the start and needs one emission`() {
        val gate = CatalogFeedGate(seedAlreadySettled = true)
        assertFalse(feedReady(gate))
        gate.onEmission()
        assertTrue(feedReady(gate))
    }

    @Test fun `no emission after settle triggers one explicit read that confirms an empty catalog`() = runTest {
        val gate = CatalogFeedGate()
        gate.onSeedSettled()
        var reads = 0
        gate.confirmIfStillUnobserved(500) { reads++; true }
        assertEquals(1, reads)
        assertTrue(feedReady(gate))
    }

    @Test fun `an emission that arrives within the timeout skips the explicit read`() = runTest {
        val gate = CatalogFeedGate()
        gate.onSeedSettled()
        var reads = 0
        val job = launch { gate.confirmIfStillUnobserved(500) { reads++; true } }
        advanceTimeBy(100)
        gate.onEmission()
        job.join()
        assertEquals(0, reads)
        assertTrue(feedReady(gate))
    }

    @Test fun `an explicit read that finds rows does not confirm empty`() = runTest {
        val gate = CatalogFeedGate()
        gate.onSeedSettled()
        gate.confirmIfStillUnobserved(500) { false }
        assertFalse(feedReady(gate))
    }
}
