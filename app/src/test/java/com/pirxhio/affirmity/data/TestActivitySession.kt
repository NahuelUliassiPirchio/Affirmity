package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.repository.*
import kotlinx.coroutines.flow.flowOf

/** Explicit fixture for unrelated features; production bundles have no default history store. */
fun testActivitySession(owner: String = "local:test") = ActivitySessionBinding(
    ActivityIdentityHandle { ActivityIdentity(owner, "test") },
    object : ActivityRepository {
        override suspend fun record(command: ActivityEvent) = ActivitySaveState.LOCAL
        override fun observeRange(owner: String, fromEpochDay: Long, toEpochDay: Long) =
            flowOf(ActivityRangeState.Ready((fromEpochDay..toEpochDay).associateWith {
                aggregateActivityDay(it, emptyList(), ActivityCoverage.UNKNOWN)
            }, 0))
        override suspend fun retryPending(owner: String) = Unit
    },
)
