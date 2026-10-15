package com.pirxhio.affirmity.widget.carousel

import com.pirxhio.affirmity.access.AdUnlockState
import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.remote.OwnedSnapshot
import com.pirxhio.affirmity.data.repository.AdUnlockRepository
import com.pirxhio.affirmity.data.repository.EntitlementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Per-read budget for remote data; the widget must render even when offline. */
const val CAROUSEL_READ_TIMEOUT_MILLIS = 4_000L

/**
 * Runs [block] and returns its result, or `null` on any failure or when [timeoutMillis] elapses.
 * `withTimeoutOrNull` alone does not catch listener errors and failed `get()` calls, which throw
 * (not just time out), so both are handled. Cancellation always propagates.
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
 * Resolves [CarouselAccess] from the entitlement and ad-unlock repositories. If
 * ANY of the three reads fails or times out, access falls back to [CarouselAccess.FailClosed]:
 * never Pro text to a user whose entitlement is unknown. `sessionUnlocks` stays empty because
 * PER_USE unlocks only live in the app process.
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

/**
 * First owned snapshot the widget may trust: an empty snapshot served only from the cache is
 * "unknown", not "no affirmations", so it is skipped. Offline with a cold cache nothing else
 * arrives and the caller's timeout maps the read to [OwnedRead.Unavailable].
 */
internal suspend fun Flow<OwnedSnapshot>.firstTrustworthyOwned(): List<AffirmationEntity> =
    first { !(it.rows.isEmpty() && it.fromCache) }.rows
