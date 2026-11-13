package com.pirxhio.affirmity.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tracks whether the catalog feed has been observed *after* the seed settled, which is the only point
 * at which an empty catalog can be called "genuinely empty". An empty emission that predates the seed
 * says nothing: the seed may be about to fill the table.
 */
class CatalogFeedGate(seedAlreadySettled: Boolean = false) {
    var seedSettled by mutableStateOf(seedAlreadySettled)
        private set

    /** True once a catalog emission arrived after the seed settled, or an empty read was confirmed. */
    var observedAfterSeed by mutableStateOf(false)
        private set

    private val postSeedObservation = CompletableDeferred<Unit>()

    fun onEmission() {
        if (seedSettled) markObserved()
    }

    private fun markObserved() {
        observedAfterSeed = true
        postSeedObservation.complete(Unit)
    }

    fun onSeedSettled() {
        seedSettled = true
    }

    /**
     * The table may not change after the seed (failed seed, empty asset), so no emission would follow.
     * If none arrived within [timeoutMillis], read once explicitly and confirm an empty result.
     */
    suspend fun confirmIfStillUnobserved(timeoutMillis: Long, readIsEmpty: suspend () -> Boolean) {
        val arrived = withTimeoutOrNull(timeoutMillis) { postSeedObservation.await() }
        if (arrived == null && readIsEmpty()) onConfirmedEmpty()
    }

    /** An explicit post-seed read found the catalog empty (the table did not change, so no emission follows). */
    fun onConfirmedEmpty() = markObserved()
}
