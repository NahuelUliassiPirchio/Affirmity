package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.ActivityEvent
import com.pirxhio.affirmity.data.ActivityRangeState
import com.pirxhio.affirmity.data.ActivitySaveState
import kotlinx.coroutines.flow.Flow

interface ActivityRepository {
    /** Returns only after durable local persistence. Failures throw, never report saved. */
    suspend fun record(command: ActivityEvent): ActivitySaveState
    fun observeRange(owner: String, fromEpochDay: Long, toEpochDay: Long): Flow<ActivityRangeState>
    suspend fun retryPending(owner: String)
    /** Gives terminally rejected rows one fresh attempt; called once per retry session. */
    suspend fun rearmFailed(owner: String)
}
