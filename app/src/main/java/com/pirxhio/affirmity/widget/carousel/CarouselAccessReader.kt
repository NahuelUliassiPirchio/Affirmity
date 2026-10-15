package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.data.repository.AdUnlockRepository
import com.pirxhio.affirmity.data.repository.EntitlementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Per-read budget for remote data; the widget must render even when offline (design D2). */
const val CAROUSEL_READ_TIMEOUT_MILLIS = 4_000L

/**
 * Runs [block] and returns its result, or `null` on any failure or when [timeoutMillis] elapses.
 * `withTimeoutOrNull` alone does not catch listener errors and failed `get()` calls, which throw
 * (W1), so both are handled. Cancellation always propagates.
 */
internal suspend fun <T : Any> guardedRead(timeoutMillis: Long, block: suspend () -> T): T? =
    try {
        withTimeoutOrNull(timeoutMillis) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

/** Reads owned affirmation ids, mapping any failure or timeout to [OwnedRead.Unavailable]. */
suspend fun readOwned(timeoutMillis: Long, block: suspend () -> List<String>): OwnedRead =
    guardedRead(timeoutMillis, block)?.let { OwnedRead.Available(it) } ?: OwnedRead.Unavailable

/**
 * Resolves [CarouselAccess] from the entitlement and ad-unlock repositories (design D1/D2). If
 * ANY of the three reads fails or times out, access falls back to [CarouselAccess.FailClosed]:
 * never Pro text to a user whose entitlement is unknown. `sessionUnlocks` stays empty because
 * PER_USE unlocks only live in the app process (W4).
 */
class CarouselAccessReader(
    private val entitlements: EntitlementRepository,
    private val adUnlocks: AdUnlockRepository,
    private val timeoutMillis: Long = CAROUSEL_READ_TIMEOUT_MILLIS,
) {
    suspend fun read(): CarouselAccess = coroutineScope {
        val entitlement = async { guardedRead(timeoutMillis) { entitlements.observe().first() } }
        val durable = async { guardedRead(timeoutMillis) { adUnlocks.getDurableUnlocks() } }
        val timed = async { guardedRead(timeoutMillis) { adUnlocks.observeTimedUnlocks().first() } }

        val resolvedEntitlement = entitlement.await()
        val resolvedDurable = durable.await()
        val resolvedTimed = timed.await()
        if (resolvedEntitlement == null || resolvedDurable == null || resolvedTimed == null) {
            CarouselAccess.FailClosed
        } else {
            CarouselAccess(
                tier = resolvedEntitlement.tier,
                grants = AdUnlockState(
                    durableUnlocks = resolvedDurable.associateBy { it.key },
                    timedUnlocks = resolvedTimed.associateBy { it.key },
                ),
            )
        }
    }
}
