package com.pirxhio.affirmity.widget.carousel

import kotlin.random.Random

/** Upper bound on the persisted queue length, keeping per-widget prefs small (design D7). */
const val MAX_ORDER = 400

/** A shuffled queue of affirmation ids and the index of the one currently shown. */
data class QueueState(val order: List<String>, val cursor: Int) {
    fun currentOrNull(): String? = order.getOrNull(cursor)
    fun current(): String = order[cursor]

    companion object {
        val Empty = QueueState(emptyList(), 0)
    }
}

/**
 * Pure shuffle queue (spec R3). Each cycle is a permutation of at most [MAX_ORDER] pool ids drawn
 * with the injected [random], so tests are deterministic. A pool larger than the cap is sampled;
 * the next cycle resamples from the then-current pool.
 */
class ShuffleQueue(private val random: Random) {

    /** Starts a fresh cycle from [pool], optionally avoiding [avoidFirst] as the first item. */
    fun start(pool: List<String>, avoidFirst: String? = null): QueueState {
        val distinct = pool.distinct()
        if (distinct.isEmpty()) return QueueState.Empty
        val order = distinct.shuffled(random).take(MAX_ORDER).toMutableList()
        if (avoidFirst != null && order.size > 1 && order[0] == avoidFirst) {
            val swapWith = 1 + random.nextInt(order.size - 1)
            order[0] = order[swapWith].also { order[swapWith] = order[0] }
        }
        return QueueState(order, 0)
    }

    /** Moves to the next item; at exhaustion starts a new cycle from [pool] (R3.2-R3.4). */
    fun advance(state: QueueState, pool: List<String>): QueueState {
        if (state.order.isEmpty()) return start(pool)
        val next = state.cursor + 1
        if (next < state.order.size) return state.copy(cursor = next)
        return start(pool, avoidFirst = state.order.last())
    }

    /**
     * Rebuilds [state] against a freshly resolved [pool] (R3.6, R8.5): ids still in the pool keep
     * their order, missing ones are dropped, the cursor is clamped, and newcomers are appended
     * only while the order is below [MAX_ORDER].
     */
    fun reconcile(state: QueueState, pool: List<String>): QueueState {
        val poolSet = pool.toSet()
        if (poolSet.isEmpty()) return QueueState.Empty
        val kept = LinkedHashSet<String>()
        var keptBeforeCursor = 0
        state.order.forEachIndexed { index, id ->
            if (id in poolSet && kept.add(id) && index < state.cursor) keptBeforeCursor++
        }
        if (kept.isEmpty()) return start(pool)
        val room = MAX_ORDER - kept.size
        val newcomers = if (room > 0) {
            pool.distinct().filter { it !in kept }.shuffled(random).take(room)
        } else {
            emptyList()
        }
        val order = kept.toList() + newcomers
        return QueueState(order, keptBeforeCursor.coerceIn(0, order.size - 1))
    }
}
