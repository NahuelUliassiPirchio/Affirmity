package com.pirxhio.affirmity.personalization.signal

import android.util.Log
import com.pirxhio.affirmity.data.local.PersonalizationSignalDao
import com.pirxhio.affirmity.data.local.PersonalizationSignalEntity
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val PRUNE_EVERY_N_INSERTS = 50
private const val RETENTION_MAX_AGE_DAYS = 180L
private const val RETENTION_MAX_ROWS = 5000
private const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
private const val TAG = "PersonalizationSignal"

/**
 * Room-backed [PersonalizationSignalRecorder]. Non-suspend, fire-and-forget (design D3, same
 * frozen shape as `AnalyticsLogger`) -- writes on [Dispatchers.IO] via the injected [scope], so
 * call sites (Slice 2) stay one line with no coroutine plumbing of their own. Failures are caught
 * and logged, never rethrown: this is a best-effort side channel that must never crash the app or
 * cancel the shared [scope] for the rest of its lifetime.
 *
 * Retention (design D7): opportunistically prunes rows older than [RETENTION_MAX_AGE_DAYS] days
 * and trims to the newest [RETENTION_MAX_ROWS], every [PRUNE_EVERY_N_INSERTS]th insert -- no
 * WorkManager, no new scheduling infrastructure (explicit design decision).
 *
 * Deviation note: design D7 says the insert counter lives "in the goals DataStore", but
 * `UserGoalsPreferences` does not exist until Slice 3. Rather than a second forward-dependency
 * detour through a dedicated DataStore file, the counter is a plain in-memory [AtomicInteger]:
 * atomic (no lost-increment race across concurrent `record()` calls, unlike a DataStore
 * read-then-write), zero disk I/O for the ~49 non-pruning inserts out of every 50, and the only
 * cost is that a process restart resets the count -- acceptable for a soft opportunistic-pruning
 * cadence protecting a 180-day/5000-row budget, not a correctness-critical counter. If Slice 3
 * wants to consolidate this into the goals store, that is a follow-up, not a blocker here.
 */
class RoomPersonalizationSignalRecorder(
    private val dao: PersonalizationSignalDao,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PersonalizationSignalRecorder {

    private val insertsSincePrune = AtomicInteger(0)

    override fun record(signal: PersonalizationSignal) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                dao.insert(signal.toEntity())
                maybePrune()
            }.onFailure { error -> Log.e(TAG, "failed to record personalization signal", error) }
        }
    }

    private suspend fun maybePrune() {
        val next = insertsSincePrune.incrementAndGet()
        if (next >= PRUNE_EVERY_N_INSERTS) {
            insertsSincePrune.set(0)
            val cutoff = nowMillis() - RETENTION_MAX_AGE_DAYS * MILLIS_PER_DAY
            dao.deleteOlderThan(cutoff)
            dao.trimToNewest(RETENTION_MAX_ROWS)
        }
    }

    private fun PersonalizationSignal.toEntity() = PersonalizationSignalEntity(
        signalType = type.name,
        themeId = themeId,
        groupId = groupId,
        tone = tone,
        occurredAtMillis = occurredAtMillis,
    )
}
