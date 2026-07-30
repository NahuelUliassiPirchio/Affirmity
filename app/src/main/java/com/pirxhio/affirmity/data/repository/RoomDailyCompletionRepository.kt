package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.DailyCompletionDao
import com.pirxhio.affirmity.data.local.DailyCompletionEntity
import kotlinx.coroutines.flow.Flow

/** Thin [DailyCompletionRepository] wrapper delegating 1:1 to the untouched [DailyCompletionDao]. */
class RoomDailyCompletionRepository(private val dao: DailyCompletionDao) : DailyCompletionRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyCompletionEntity>> =
        dao.observeRange(from, to)

    override suspend fun getRange(from: Long, to: Long): List<DailyCompletionEntity> =
        dao.getRange(from, to)

    override suspend fun markMeditation(epochDay: Long) = dao.markMeditation(epochDay)
    override suspend fun markAffirmation(epochDay: Long) = dao.markAffirmation(epochDay)
}
