package com.pirxhio.affirmity.data.remote

import com.pirxhio.affirmity.data.local.ACTIVITY_PENDING_PAGE_SIZE
import com.pirxhio.affirmity.data.local.ActivityDao
import com.pirxhio.affirmity.data.local.ActivityEventEntity
import com.pirxhio.affirmity.data.ActivityCoverageInterval
import com.pirxhio.affirmity.data.ActivityEvent
import com.pirxhio.affirmity.data.ActivityRangeState
import com.pirxhio.affirmity.data.ActivityCoverage
import com.pirxhio.affirmity.data.ActivitySaveState
import com.pirxhio.affirmity.data.aggregateActivityDay
import com.pirxhio.affirmity.data.mergeActivityEvents
import com.pirxhio.affirmity.data.local.ActivityCoverageEntity
import com.pirxhio.affirmity.data.local.domain
import com.pirxhio.affirmity.data.local.stored
import com.pirxhio.affirmity.data.repository.ActivityRepository
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestoreException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

fun coverageIdentity(interval: ActivityCoverageInterval): String {
    require(interval.sourceId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
    val canonical = "activity-coverage-v1\n${interval.sourceId}\n${interval.recordingSchema}\n" +
        "${interval.fromEpochDay}\n${interval.toEpochDay}\n${if (interval.uncertain) 1 else 0}"
    val alphabet = "0123456789abcdef"
    return buildString {
        MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).forEach { byte ->
            val value = byte.toInt() and 255
            append(alphabet[value shr 4])
            append(alphabet[value and 15])
        }
    }
}

fun interface ActivityUploader {
    suspend fun upload(uid: String, event: ActivityEventEntity)
}

/**
 * Permanent rejections (and payloads that can never validate) are marked FAILED and skipped so one bad row cannot
 * stall the queue; FAILED rows are never deleted. Everything else is transient and keeps the row PENDING.
 */
fun isTerminalUploadFailure(failure: Throwable): Boolean = when (failure) {
    is FirebaseFirestoreException -> failure.code in TERMINAL_FIRESTORE_CODES
    is IllegalArgumentException -> true
    else -> false
}

private const val TAG = "ActivitySync"

/** Logs identity and failure kind only: never payload content or the uid. */
private fun logTerminalFailure(kind: String, id: String, failure: Exception) {
    val reason = (failure as? FirebaseFirestoreException)?.code?.name ?: failure.javaClass.simpleName
    Log.w(TAG, "Activity $kind $id rejected permanently ($reason); marked FAILED")
}

private val TERMINAL_FIRESTORE_CODES = setOf(
    FirebaseFirestoreException.Code.PERMISSION_DENIED,
    FirebaseFirestoreException.Code.INVALID_ARGUMENT,
    FirebaseFirestoreException.Code.FAILED_PRECONDITION,
)

/** Remembers only the identity keys of the previous page, so tracking stays O(page size). */
internal class PendingPageTracker<K> {
    private var previous: Set<K> = emptySet()
    val trackedCount: Int get() = previous.size

    /** True when any key repeats from the previous page: a row stayed PENDING, so the pass made no progress. */
    fun stalled(keys: List<K>): Boolean = keys.any { it in previous }

    fun remember(keys: List<K>) { previous = keys.toHashSet() }
}

private data class CoverageKey(val sourceId: String, val schema: Int, val from: Long, val to: Long, val uncertain: Boolean)

private fun ActivityCoverageEntity.key() = CoverageKey(sourceId, recordingSchema, fromEpochDay, toEpochDay, uncertain)

/**
 * Drains a pending queue page by page so a backlog completes in one pass without loading it all.
 * [handle] returns false to stop the pass (uid guard); a throw also stops it (transient failure).
 * Handled rows leave PENDING (acknowledged or FAILED) and pages are oldest first, so a row that stayed PENDING without
 * throwing reappears in the very next page. A page repeating any key of the previous page ends the pass before that
 * row is handled again; an empty or short page also ends it, so the loop cannot spin.
 */
internal suspend fun <T, K> drainPendingPages(
    fetch: suspend (limit: Int) -> List<T>,
    key: (T) -> K,
    handle: suspend (T) -> Boolean,
) {
    val tracker = PendingPageTracker<K>()
    while (true) {
        val page = fetch(ACTIVITY_PENDING_PAGE_SIZE)
        if (page.isEmpty()) return
        val keys = page.map(key)
        if (tracker.stalled(keys)) return
        for (row in page) if (!handle(row)) return
        if (page.size < ACTIVITY_PENDING_PAGE_SIZE) return
        tracker.remember(keys)
    }
}

/** UID is immutable; late acknowledgements update A's journal, never whichever user is now active. */
class ActivityEventSync(
    private val dao: ActivityDao,
    private val uid: String,
    private val uploader: ActivityUploader,
    private val currentUid: () -> String?,
) {
    private val mutex = Mutex()

    suspend fun retryPending() = mutex.withLock {
        if (currentUid() != uid) return@withLock
        val owner = "account:$uid"
        drainPendingPages({ limit -> dao.pending(owner, limit) }, { it.eventId }) { event ->
            if (currentUid() != uid) return@drainPendingPages false
            require(event.owner == owner) { "Foreign activity owner" }
            try {
                withTimeout(30_000) { uploader.upload(uid, event) }
            } catch (failure: Exception) {
                if (!isTerminalUploadFailure(failure)) throw failure
                dao.markFailed(owner, event.eventId)
                logTerminalFailure("event", event.eventId, failure)
                return@drainPendingPages true
            }
            dao.acknowledge(owner, event.eventId)
            true
        }
    }
}

interface ActivityRemoteClient {
    suspend fun uploadEvent(uid: String, event: ActivityEventEntity)
    suspend fun uploadCoverage(uid: String, coverage: ActivityCoverageEntity)
    fun observeEvents(uid: String, from: Long, to: Long): Flow<List<ActivityEventEntity>>
    fun observeCoverage(uid: String, from: Long, to: Long): Flow<List<ActivityCoverageEntity>>
}

class FirestoreActivityRepository(
    private val dao: ActivityDao,
    private val uid: String,
    private val remote: ActivityRemoteClient,
    private val currentUid: () -> String?,
) : ActivityRepository {
    private val owner = "account:$uid"
    private val sync = ActivityEventSync(dao, uid, ActivityUploader(remote::uploadEvent), currentUid)
    private val retryMutex = Mutex()
    init { require(uid.isNotBlank() && '/' !in uid) }

    override suspend fun record(command: ActivityEvent): ActivitySaveState {
        require(command.owner == owner) { "Foreign activity owner" }
        val payload = command.stored("PENDING")
        activityEventFromMap(owner, command.eventId, activityEventToMap(payload))
        return ActivitySaveState.valueOf(dao.record(payload).uploadState)
    }

    override fun observeRange(owner: String, fromEpochDay: Long, toEpochDay: Long): Flow<ActivityRangeState> {
        require(owner == this.owner && toEpochDay >= fromEpochDay && toEpochDay - fromEpochDay <= 41)
        if (currentUid() != uid) return flowOf(ActivityRangeState.Loading)
        return combine(remote.observeEvents(uid, fromEpochDay, toEpochDay),
            remote.observeCoverage(uid, fromEpochDay, toEpochDay),
            dao.observeRange(owner, fromEpochDay, toEpochDay), dao.observeCoverage(owner, fromEpochDay, toEpochDay)) {
                remoteEvents, remoteCoverage, localEvents, localCoverage ->
            if (currentUid() != uid) return@combine ActivityRangeState.Loading
            require((remoteCoverage + localCoverage).all { it.owner == owner }) { "Foreign coverage owner" }
            val pending = localEvents.filter { it.uploadState == "PENDING" }
            val events = mergeActivityEvents(owner, remoteEvents.map { it.domain() }, localEvents.map { it.domain() })
            accountRange(fromEpochDay, toEpochDay, events,
                pending.size + localCoverage.count { it.uploadState == "PENDING" })
        }.onStart { emit(ActivityRangeState.Loading) }.catch { failure ->
            if (failure is CancellationException) throw failure
            emit(ActivityRangeState.Error(failure))
        }
    }

    override suspend fun rearmFailed(owner: String) = retryMutex.withLock {
        require(owner == this.owner) { "Foreign activity owner" }
        if (currentUid() != uid) return@withLock
        dao.rearmFailed(owner)
        dao.rearmFailedCoverage(owner)
    }

    override suspend fun retryPending(owner: String) = retryMutex.withLock {
        require(owner == this.owner) { "Foreign activity owner" }
        if (currentUid() != uid) return@withLock
        sync.retryPending()
        drainPendingPages({ limit -> dao.pendingCoverage(owner, limit) }, { it.key() }) { coverage ->
            if (currentUid() != uid) return@drainPendingPages false
            require(coverage.owner == owner)
            try {
                withTimeout(30_000) { remote.uploadCoverage(uid, coverage) }
            } catch (failure: Exception) {
                if (!isTerminalUploadFailure(failure)) throw failure
                dao.markCoverageFailed(coverage)
                logTerminalFailure("coverage", coverageIdentity(ActivityCoverageInterval(coverage.owner, coverage.sourceId,
                    coverage.fromEpochDay, coverage.toEpochDay, coverage.recordingSchema, coverage.uncertain)), failure)
                return@drainPendingPages true
            }
            dao.acknowledgeCoverage(coverage)
            true
        }
    }
}

private fun accountRange(from: Long, to: Long, events: List<ActivityEvent>, pendingCount: Int): ActivityRangeState =
    ActivityRangeState.Ready((from..to).associateWith { day ->
        aggregateActivityDay(day, events, ActivityCoverage.UNKNOWN)
    }, pendingCount)

/** Restart calls retry immediately; identity changes stop retries without discarding durable rows. */
suspend fun runActivityRetryLoop(
    repository: ActivityRepository,
    owner: String,
    currentOwner: () -> String?,
    onFailure: (Throwable?) -> Unit,
) {
    var failures = 0
    while (currentOwner() == owner) {
        val waitMillis = try {
            repository.retryPending(owner)
            if (currentOwner() != owner) return
            onFailure(null)
            failures = 0
            60_000L
        } catch (failure: Exception) {
            if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
            currentCoroutineContext().ensureActive() // an outer timeout/cancel is not a retry failure
            if (currentOwner() != owner) return
            onFailure(failure)
            val backoff = (1_000L shl failures.coerceAtMost(6)).coerceAtMost(60_000L)
            failures = (failures + 1).coerceAtMost(6)
            backoff
        }
        delay(waitMillis)
    }
}

/** Caller cancellation ends both the backoff timer and connectivity/new-record subscription. */
suspend fun runActivityRetrySession(repository: ActivityRepository, owner: String,
    currentOwner: () -> String?, signals: Flow<Unit>, onFailure: (Throwable?) -> Unit) = coroutineScope {
    if (currentOwner() == owner) {
        try { repository.rearmFailed(owner) }
        catch (failure: Exception) {
            if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
            currentCoroutineContext().ensureActive() // an outer timeout/cancel is not a retry failure
            if (currentOwner() == owner) onFailure(failure)
        }
    }
    launch { runActivityRetryLoop(repository, owner, currentOwner, onFailure) }
    signals.collect {
        if (currentOwner() == owner) {
            try { repository.retryPending(owner); if (currentOwner() == owner) onFailure(null) }
            catch (failure: Exception) {
                if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                currentCoroutineContext().ensureActive() // an outer timeout/cancel is not a retry failure
                if (currentOwner() == owner) onFailure(failure)
            }
        }
    }
}
