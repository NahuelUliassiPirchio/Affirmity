package com.pirxhio.affirmity.widget.carousel

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleQueueTest {

    private fun pool(n: Int) = (1..n).map { "id$it" }

    private fun queue(seed: Int = 1) = ShuffleQueue(Random(seed))

    @Test
    fun `new cycle is a permutation of the pool`() {
        val state = queue().start(pool(5))

        assertEquals(pool(5).toSet(), state.order.toSet())
        assertEquals(5, state.order.size)
        assertEquals(0, state.cursor)
    }

    @Test
    fun `advancing through a full cycle shows each item once`() {
        val q = queue()
        var state = q.start(pool(5))
        val shown = mutableListOf(state.current())
        repeat(4) {
            state = q.advance(state, pool(5))
            shown += state.current()
        }

        assertEquals(5, shown.toSet().size)
    }

    @Test
    fun `exhaustion reshuffles at position zero and the first item differs from the previous last`() {
        for (seed in 0 until 50) {
            val q = queue(seed)
            var state = q.start(pool(5))
            repeat(4) { state = q.advance(state, pool(5)) }
            val previousLast = state.current()

            state = q.advance(state, pool(5))

            assertEquals(0, state.cursor)
            assertEquals(5, state.order.size)
            assertEquals(pool(5).toSet(), state.order.toSet())
            assertNotEquals("seed $seed", previousLast, state.current())
        }
    }

    @Test
    fun `pool of one always shows that item`() {
        val q = queue()
        var state = q.start(pool(1))
        repeat(5) {
            state = q.advance(state, pool(1))
            assertEquals("id1", state.current())
        }
    }

    @Test
    fun `empty pool yields an empty state`() {
        val q = queue()
        val state = q.start(emptyList())
        assertTrue(state.order.isEmpty())
        assertEquals(null, state.currentOrNull())
        assertEquals(state, q.advance(state, emptyList()))
    }

    @Test
    fun `same seed gives the same sequence`() {
        fun sequence(): List<String> {
            val q = queue(42)
            var state = q.start(pool(7))
            return List(20) { state.current().also { state = q.advance(state, pool(7)) } }
        }

        assertEquals(sequence(), sequence())
    }

    @Test
    fun `reconcile clamps cursor and drops items no longer in the pool`() {
        val q = queue()
        val state = QueueState(order = pool(5), cursor = 4)

        val result = q.reconcile(state, listOf("id1", "id2"))

        assertTrue(result.cursor in 0..1)
        assertEquals(setOf("id1", "id2"), result.order.toSet())
        assertEquals(2, result.order.size)
    }

    @Test
    fun `reconcile with an unchanged pool keeps order and cursor`() {
        val q = queue()
        val state = QueueState(order = listOf("id3", "id1", "id2"), cursor = 1)

        assertEquals(state, q.reconcile(state, pool(3)))
    }

    @Test
    fun `reconcile keeps the current item when it stays in the pool`() {
        val q = queue()
        val state = QueueState(order = listOf("a", "b", "c", "d"), cursor = 2)

        val result = q.reconcile(state, listOf("a", "c", "d"))

        assertEquals("c", result.current())
    }

    @Test
    fun `reconcile appends newcomers after the existing order`() {
        val q = queue()
        val state = QueueState(order = listOf("a", "b"), cursor = 1)

        val result = q.reconcile(state, listOf("a", "b", "c", "d"))

        assertEquals(listOf("a", "b"), result.order.take(2))
        assertEquals(setOf("c", "d"), result.order.drop(2).toSet())
        assertEquals(1, result.cursor)
    }

    @Test
    fun `reconcile of an empty state starts a fresh cycle`() {
        val result = queue().reconcile(QueueState(emptyList(), 0), pool(4))

        assertEquals(pool(4).toSet(), result.order.toSet())
        assertEquals(0, result.cursor)
    }

    @Test
    fun `reconcile to an empty pool empties the queue`() {
        val result = queue().reconcile(QueueState(pool(3), 1), emptyList())

        assertTrue(result.order.isEmpty())
        assertEquals(0, result.cursor)
    }

    @Test
    fun `a large pool is capped to 400 ids`() {
        val state = queue().start(pool(1000))

        assertEquals(MAX_ORDER, state.order.size)
        assertEquals(400, state.order.size)
        assertEquals(400, state.order.toSet().size)
        assertTrue(pool(1000).toSet().containsAll(state.order))
    }

    @Test
    fun `reconcile keeps the sampled order stable for a large unchanged pool`() {
        val q = queue()
        val state = q.start(pool(1000))

        assertEquals(state, q.reconcile(state, pool(1000)))
    }

    @Test
    fun `newcomers are only added while the order is below the cap`() {
        val q = queue()
        val full = q.start(pool(1000))

        val withNewcomer = q.reconcile(full, pool(1000) + "extra")
        assertEquals(full, withNewcomer)

        val dropped = full.order.first()
        val refilled = q.reconcile(full, (pool(1000) - dropped) + "extra")
        assertEquals(MAX_ORDER, refilled.order.size)
        assertTrue(dropped !in refilled.order)
    }

    @Test
    fun `exhausting a capped cycle resamples from the current pool`() {
        val q = queue()
        var state = q.start(pool(1000))
        val firstSample = state.order.toSet()
        repeat(MAX_ORDER - 1) { state = q.advance(state, pool(1000)) }
        assertEquals(MAX_ORDER - 1, state.cursor)

        val last = state.current()
        state = q.advance(state, pool(1000))

        assertEquals(0, state.cursor)
        assertEquals(MAX_ORDER, state.order.size)
        assertEquals(MAX_ORDER, state.order.toSet().size)
        assertNotEquals(last, state.current())
        assertNotEquals("resample should differ from the first sample", firstSample, state.order.toSet())
    }
}
