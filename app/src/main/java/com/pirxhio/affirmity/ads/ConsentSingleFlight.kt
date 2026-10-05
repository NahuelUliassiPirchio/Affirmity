package com.pirxhio.affirmity.ads

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Upper bound for one consent request. Long enough for a user to read and answer the consent form,
 * short enough that a request whose SDK callback never fires cannot block every later caller.
 */
internal const val CONSENT_REQUEST_TIMEOUT_MS = 60_000L

/**
 * Process-wide single-flight for the UMP consent request, free of SDK types so it is unit-tested.
 *
 * At most one request is in flight. A caller arriving while one runs awaits the SAME result instead
 * of starting another, and the request runs in [scope], so cancelling a caller (for example a
 * `LaunchedEffect` cancelled by Activity recreation) cancels only its await, never the request.
 * Once a request ends the slot is free again: [run] always starts a fresh one, while
 * [runOnceAfterSuccess] returns null after any request has completed successfully (no launch
 * re-run). A [ConsentGatherResult.Failed] or a thrown request leaves the door open for a retry.
 */
internal class ConsentSingleFlight(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = CONSENT_REQUEST_TIMEOUT_MS,
) {

    private val lock = Any()
    private var inFlight: Deferred<ConsentGatherResult>? = null
    private var succeeded = false

    /** Joins the in-flight request, or starts [request]. Exceptions of the request propagate. */
    suspend fun run(request: suspend () -> ConsentGatherResult): ConsentGatherResult =
        acquire(request, skipIfSucceeded = false)!!.await()

    /** Like [run], but returns null without requesting when a request already succeeded. */
    suspend fun runOnceAfterSuccess(request: suspend () -> ConsentGatherResult): ConsentGatherResult? =
        acquire(request, skipIfSucceeded = true)?.await()

    private fun acquire(
        request: suspend () -> ConsentGatherResult,
        skipIfSucceeded: Boolean,
    ): Deferred<ConsentGatherResult>? {
        val started: Deferred<ConsentGatherResult>
        synchronized(lock) {
            inFlight?.let { return it }
            if (skipIfSucceeded && succeeded) return null
            // LAZY: the slot is assigned before the body can run, so its cleanup never races it.
            started = scope.async(start = CoroutineStart.LAZY) {
                var result: ConsentGatherResult? = null
                try {
                    // Bounded so the slot is always freed even if the SDK never calls back (e.g.
                    // its Activity was destroyed mid-flow). withTimeoutOrNull only swallows its own
                    // timeout; a real cancellation of the scope still propagates.
                    result = withTimeoutOrNull(timeoutMs) { request() }
                        ?: ConsentGatherResult.Failed("consent request timed out")
                    result
                } finally {
                    synchronized(lock) {
                        inFlight = null
                        if (result is ConsentGatherResult.Completed) succeeded = true
                    }
                }
            }
            inFlight = started
        }
        started.start()
        return started
    }
}
