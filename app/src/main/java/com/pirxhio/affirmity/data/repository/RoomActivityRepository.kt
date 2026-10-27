package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.ActivityCoverageInterval
import com.pirxhio.affirmity.data.ActivityEvent
import com.pirxhio.affirmity.data.ActivityRangeState
import com.pirxhio.affirmity.data.ActivitySaveState
import com.pirxhio.affirmity.data.aggregateActivityDay
import com.pirxhio.affirmity.data.resolveActivityCoverage
import com.pirxhio.affirmity.data.local.ActivityDao
import com.pirxhio.affirmity.data.local.domain
import com.pirxhio.affirmity.data.local.stored
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

/** Fixed generation, including after a caller suspends. Room enforces the claim/write barrier. */
class RoomActivityRepository(private val dao: ActivityDao, private val owner: String) : ActivityRepository {
    init { require(owner.startsWith("local:")) }

    override suspend fun record(command: ActivityEvent): ActivitySaveState {
        require(command.owner == owner) { "Foreign activity owner" }
        return ActivitySaveState.valueOf(dao.record(command.stored("LOCAL")).uploadState)
    }

    override fun observeRange(owner: String, fromEpochDay: Long, toEpochDay: Long): Flow<ActivityRangeState> {
        require(owner == this.owner && toEpochDay >= fromEpochDay && toEpochDay - fromEpochDay <= 41)
        return combine(dao.observeRange(owner, fromEpochDay, toEpochDay),
            dao.observeCoverage(owner, fromEpochDay, toEpochDay)) { events, coverage ->
            require(events.all { it.owner == owner } && coverage.all { it.owner == owner })
            val intervals = coverage.map { ActivityCoverageInterval(it.owner, it.sourceId,
                it.fromEpochDay, it.toEpochDay, it.recordingSchema, it.uncertain) }
            ActivityRangeState.Ready((fromEpochDay..toEpochDay).associateWith { day ->
                aggregateActivityDay(day, events.map { it.domain() }, resolveActivityCoverage(owner, day, intervals))
            }, pendingCount = 0) as ActivityRangeState
        }.onStart { emit(ActivityRangeState.Loading) }.catch { failure ->
            if (failure is CancellationException) throw failure
            emit(ActivityRangeState.Error(failure))
        }
    }

    override suspend fun retryPending(owner: String) { require(owner == this.owner) }
    override suspend fun rearmFailed(owner: String) { require(owner == this.owner) }
}
