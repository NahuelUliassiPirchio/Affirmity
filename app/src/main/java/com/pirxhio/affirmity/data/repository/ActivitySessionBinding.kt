package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.ActivityEvent
import com.pirxhio.affirmity.data.local.ActivityDao
import com.pirxhio.affirmity.data.local.ActivityDailyFactDao
import com.pirxhio.affirmity.data.local.ActivityOwnerEntity
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import com.pirxhio.affirmity.data.ActivityRangeState

fun unavailableActivitySession(uid: String, failure: Throwable? = null) = ActivitySessionBinding(
    ActivityIdentityHandle { ActivityIdentity("account:$uid", "unavailable") },
    object : ActivityRepository {
        override suspend fun record(command: ActivityEvent): com.pirxhio.affirmity.data.ActivitySaveState =
            throw IllegalStateException("Account activity is not ready", failure)
        override fun observeRange(owner: String, fromEpochDay: Long, toEpochDay: Long) =
            flowOf(if (failure == null) ActivityRangeState.Loading else ActivityRangeState.Error(failure))
        override suspend fun retryPending(owner: String) = Unit
        override suspend fun rearmFailed(owner: String) = Unit
    },
)

data class ActivityIdentity(val owner: String, val sourceId: String)

/** The handle itself is captured synchronously; initialization cannot redirect it on auth change. */
class ActivityIdentityHandle(private val resolve: suspend () -> ActivityIdentity) {
    suspend fun await(): ActivityIdentity = resolve()
}

class ActivitySessionBinding(
    val identity: ActivityIdentityHandle,
    val repository: ActivityRepository,
)

/** Reads and writes await ONE immutable generation, never the process's latest active owner. */
class RoomActivitySession(
    private val owner: Deferred<ActivityOwnerEntity>,
    private val journal: ActivityDao,
    private val daily: ActivityDailyFactDao,
) {
    val activity = ActivitySessionBinding(ActivityIdentityHandle { owner.await().let { ActivityIdentity(it.owner, it.sourceId) } },
        object : ActivityRepository {
            override suspend fun record(command: ActivityEvent) = repository().record(command)
            override fun observeRange(owner: String, fromEpochDay: Long, toEpochDay: Long) = flow {
                emitAll(repository().observeRange(owner, fromEpochDay, toEpochDay))
            }
            override suspend fun retryPending(owner: String) = repository().retryPending(owner)
            override suspend fun rearmFailed(owner: String) = repository().rearmFailed(owner)
            private suspend fun repository() = RoomActivityRepository(journal, owner.await().owner)
        })

    val moods: DailyMoodRepository = object : DailyMoodRepository {
        private suspend fun delegate() = ScopedRoomDailyMoodRepository(daily, owner.await().owner)
        override fun observeRange(from: Long, to: Long) = flow { emitAll(delegate().observeRange(from, to)) }
        override suspend fun getRange(from: Long, to: Long) = delegate().getRange(from, to)
        override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) = delegate().upsert(epochDay, moodValue, note)
    }
    val completions: DailyCompletionRepository = object : DailyCompletionRepository {
        private suspend fun delegate() = ScopedRoomDailyCompletionRepository(daily, owner.await().owner)
        override fun observeRange(from: Long, to: Long) = flow { emitAll(delegate().observeRange(from, to)) }
        override suspend fun getRange(from: Long, to: Long) = delegate().getRange(from, to)
        override suspend fun markMeditation(epochDay: Long) = delegate().markMeditation(epochDay)
        override suspend fun markAffirmation(epochDay: Long) = delegate().markAffirmation(epochDay)
        override suspend fun earliestEpochDay() = delegate().earliestEpochDay()
    }
    val healerUses: StreakHealerRepository = object : StreakHealerRepository {
        private suspend fun delegate() = ScopedRoomStreakHealerRepository(daily, owner.await().owner)
        override fun observeRange(from: Long, to: Long) = flow { emitAll(delegate().observeRange(from, to)) }
        override suspend fun getRange(from: Long, to: Long) = delegate().getRange(from, to)
        override suspend fun recordUse(healedEpochDay: Long) = delegate().recordUse(healedEpochDay)
    }
}
