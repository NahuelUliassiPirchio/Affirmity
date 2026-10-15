package com.pirxhio.affirmity.widget.carousel

import kotlinx.coroutines.CancellationException

/** Per-instance result of one rotation pass. */
sealed interface RotationOutcome {
    data object Advanced : RotationOutcome
    data class Failed(val error: Throwable) : RotationOutcome
}

object CarouselRotationRunner {
    /**
     * Runs [step] for every id, isolating failures (R10.2): an exception on one instance is
     * recorded and the rest still run. [CancellationException] is always rethrown.
     */
    suspend fun <Id> runAll(ids: List<Id>, step: suspend (Id) -> Unit): Map<Id, RotationOutcome> {
        val outcomes = LinkedHashMap<Id, RotationOutcome>()
        for (id in ids) {
            outcomes[id] = try {
                step(id)
                RotationOutcome.Advanced
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RotationOutcome.Failed(e)
            }
        }
        return outcomes
    }
}

/** Minimal per-instance state access, so the ordering contract below is testable on the JVM. */
interface CarouselStateStore {
    /** Atomically applies [transform] and returns the state as it was BEFORE the update. */
    suspend fun update(transform: (CarouselState) -> CarouselState): CarouselState
}

/**
 * Reads AND clears `advancePending` in one update before [load] starts (D4/W3). The flag is held
 * in memory for the call, so a failed load cannot strand it: the worst case is one skipped advance.
 */
suspend fun <T> withAdvanceConsumed(store: CarouselStateStore, load: suspend (advance: Boolean) -> T): T {
    val previous = store.update { it.copy(advancePending = false) }
    return load(previous.advancePending)
}

object CarouselRotation {
    /**
     * Pure core of one render: reconciles the stored queue against the freshly built [pool]
     * (R3.6, R8.5), then moves one position forward when [advance] is set.
     */
    fun next(state: CarouselState, pool: List<String>, queue: ShuffleQueue, advance: Boolean): CarouselState {
        val reconciled = queue.reconcile(state.queue, pool)
        val result = if (advance) queue.advance(reconciled, pool) else reconciled
        return state.copy(order = result.order, cursor = result.cursor)
    }
}
