package com.pirxhio.affirmity.data.remote

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
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
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
        for (event in dao.pending(owner)) {
            if (currentUid() != uid) return@withLock
            require(event.owner == owner) { "Foreign activity owner" }
            withTimeout(30_000) { uploader.upload(uid, event) }
            dao.acknowledge(owner, event.eventId)
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

    override suspend fun retryPending(owner: String) = retryMutex.withLock {
        require(owner == this.owner) { "Foreign activity owner" }
        if (currentUid() != uid) return@withLock
        sync.retryPending()
        for (coverage in dao.pendingCoverage(owner)) {
            if (currentUid() != uid) return@withLock
            require(coverage.owner == owner)
            withTimeout(30_000) { remote.uploadCoverage(uid, coverage) }
            dao.acknowledgeCoverage(coverage)
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
    launch { runActivityRetryLoop(repository, owner, currentOwner, onFailure) }
    signals.collect {
        if (currentOwner() == owner) {
            try { repository.retryPending(owner); if (currentOwner() == owner) onFailure(null) }
            catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (currentOwner() == owner) onFailure(failure)
            }
        }
    }
}
