package com.pirxhio.affirmity.widget.carousel

import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarouselRotationRunnerTest {

    private class InMemoryStore(initial: CarouselState) : CarouselStateStore {
        var state = initial
        override suspend fun update(transform: (CarouselState) -> CarouselState): CarouselState {
            val previous = state
            state = transform(previous)
            return previous
        }
    }

    private val pool = (0 until 10).map { "id$it" }
    private val queue = ShuffleQueue(Random(7))

    private fun stateAt(position: Int, order: List<String> = pool) =
        CarouselState(source = CarouselSource.Favorites, order = order, cursor = position)

    /** What `provideGlance` does for one instance, minus I/O. */
    private suspend fun render(store: InMemoryStore, load: suspend () -> List<String> = { pool }) {
        val captured = captureForLoad(store)
        val resolvedPool = load()
        store.update { CarouselRotation.next(it, resolvedPool, queue, captured.advancePending) }
    }

    private suspend fun rotate(stores: Map<Int, InMemoryStore>, load: suspend (Int) -> List<String> = { pool }) =
        CarouselRotationRunner.runAll(stores.keys.toList()) { id ->
            val store = stores.getValue(id)
            store.update { it.copy(advancePending = true) }
            render(store) { load(id) }
        }


    @Test
    fun `each instance advances by one position`() = runTest {
        val stores = mapOf(1 to InMemoryStore(stateAt(0)), 2 to InMemoryStore(stateAt(3)))

        rotate(stores)

        assertEquals(1, stores.getValue(1).state.cursor)
        assertEquals(4, stores.getValue(2).state.cursor)
    }

    @Test
    fun `no advance happens without the pending flag`() = runTest {
        val store = InMemoryStore(stateAt(2))

        render(store)

        assertEquals(2, store.state.cursor)
    }

    @Test
    fun `an unchanged pool keeps order and cursor on a plain render`() = runTest {
        val store = InMemoryStore(stateAt(5))

        render(store)

        assertEquals(stateAt(5), store.state)
    }


    @Test
    fun `instances advance within their own queues`() = runTest {
        val poolA = listOf("a1", "a2", "a3")
        val poolB = listOf("b1", "b2", "b3", "b4")
        val stores = mapOf(
            1 to InMemoryStore(stateAt(0, poolA)),
            2 to InMemoryStore(stateAt(0, poolB)),
        )

        rotate(stores) { id -> if (id == 1) poolA else poolB }

        assertEquals("a2", stores.getValue(1).state.queue.currentOrNull())
        assertEquals("b2", stores.getValue(2).state.queue.currentOrNull())
    }


    @Test
    fun `a failing instance does not stop the others`() = runTest {
        val stores = mapOf(1 to InMemoryStore(stateAt(0)), 2 to InMemoryStore(stateAt(3)))

        val outcomes = rotate(stores) { id -> if (id == 1) error("boom") else pool }

        assertTrue(outcomes.getValue(1) is RotationOutcome.Failed)
        assertEquals(RotationOutcome.Advanced, outcomes.getValue(2))
        assertEquals(4, stores.getValue(2).state.cursor)
    }

    @Test
    fun `cancellation is rethrown and not recorded as a failure`() = runTest {
        var thrown: Throwable? = null
        try {
            CarouselRotationRunner.runAll(listOf(1, 2)) { throw CancellationException("stop") }
        } catch (e: CancellationException) {
            thrown = e
        }

        assertTrue(thrown is CancellationException)
    }

    // --- the pending flag is consumed before the heavy load ---

    @Test
    fun `the flag is read and cleared before the load runs`() = runTest {
        val store = InMemoryStore(stateAt(0).copy(advancePending = true))

        val captured = captureForLoad(store)

        assertEquals(true, captured.advancePending)
        assertEquals(false, store.state.advancePending)
    }

    @Test
    fun `a failed load cannot strand the pending flag`() = runTest {
        val store = InMemoryStore(stateAt(0).copy(advancePending = true))

        runCatching { render(store) { error("firestore down") } }

        assertFalse(store.state.advancePending)
    }

    // --- requestAdvance ---

    @Test
    fun `requestAdvance leaves the flag raised when the update request succeeds`() = runTest {
        val store = InMemoryStore(stateAt(0))

        requestAdvance(store) { }

        assertTrue(store.state.advancePending)
    }

    @Test
    fun `requestAdvance clears the flag when the update request fails`() = runTest {
        val store = InMemoryStore(stateAt(0))

        val outcomes = CarouselRotationRunner.runAll(listOf(1)) {
            requestAdvance(store) { throw java.io.IOException("update failed") }
        }

        assertTrue(outcomes.getValue(1) is RotationOutcome.Failed)
        assertFalse(store.state.advancePending)
    }

    @Test
    fun `requestAdvance clears the flag and rethrows on cancellation`() = runTest {
        val store = InMemoryStore(stateAt(0))
        var cancelled = false

        try {
            requestAdvance(store) { throw CancellationException("stop") }
        } catch (e: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
        assertFalse(store.state.advancePending)
    }

    @Test
    fun `requestAdvance requests a refresh before the update so a live session renders the advance`() = runTest {
        val store = InMemoryStore(stateAt(0).copy(refreshNonce = 4))
        var nonceSeenByUpdate = -1L

        requestAdvance(store) { nonceSeenByUpdate = store.state.refreshNonce }

        assertEquals(5L, nonceSeenByUpdate)
    }

    @Test
    fun `requestRefresh raises the nonce before the update without advancing`() = runTest {
        val store = InMemoryStore(stateAt(2).copy(refreshNonce = 0))
        var nonceSeenByUpdate = -1L

        requestRefresh(store) { nonceSeenByUpdate = store.state.refreshNonce }

        assertEquals(1L, nonceSeenByUpdate)
        assertFalse(store.state.advancePending)
        assertEquals(2, store.state.cursor)
    }
}
