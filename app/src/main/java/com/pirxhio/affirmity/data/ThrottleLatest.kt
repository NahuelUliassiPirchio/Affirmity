package com.pirxhio.affirmity.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select

/**
 * Window for coalescing view-triggered upload signals. It merges upload passes (and the radio
 * wakeups they cost); it does not reduce Firestore writes, since every view is its own document.
 */
const val ACTIVITY_VIEW_SIGNAL_WINDOW_MILLIS = 10_000L

/**
 * Throttle-latest: the first value of a quiet period is emitted immediately, later values within
 * [windowMillis] collapse into one trailing emission at the window end (which opens a new window).
 * The window opens after the downstream [emit] returns, on purpose: while a pass is still running,
 * coalescing more signals is the goal. Upstream is conflated, so only the latest value survives
 * backpressure. Unlike a debounce, continuous input cannot postpone emission. Cancelling the collector cancels
 * the pending window timer; a pending trailing value is then dropped.
 */
fun <T> Flow<T>.throttleLatest(windowMillis: Long): Flow<T> = flow {
    coroutineScope {
        val upstream = buffer(Channel.CONFLATED).produceIn(this)
        var next: T = upstream.receiveCatching().let { if (it.isClosed) return@coroutineScope else it.getOrThrow() }
        while (true) {
            emit(next)
            // select chooses atomically between "window elapsed" and "element received", so the timer
            // never cancels a receive; the window clause is registered first because select is biased
            // and a busy queue must not starve it. External cancellation may still drop an element.
            val window = async { delay(windowMillis) }
            var latest: T? = null
            var hasLatest = false
            var closed = false
            var elapsed = false
            while (!elapsed && !closed) {
                select<Unit> {
                    window.onAwait { elapsed = true }
                    upstream.onReceiveCatching { result ->
                        if (result.isClosed) { result.exceptionOrNull()?.let { throw it }; closed = true }
                        else { latest = result.getOrThrow(); hasLatest = true }
                    }
                }
            }
            window.cancel()
            if (hasLatest) {
                @Suppress("UNCHECKED_CAST")
                next = latest as T
                if (closed) { emit(next); return@coroutineScope }
            } else {
                if (closed) return@coroutineScope
                next = upstream.receiveCatching().let { if (it.isClosed) return@coroutineScope else it.getOrThrow() }
            }
        }
    }
}
