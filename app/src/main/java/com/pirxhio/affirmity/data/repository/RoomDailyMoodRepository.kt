package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.DailyMoodDao
import com.pirxhio.affirmity.data.local.DailyMoodEntity
import kotlinx.coroutines.flow.Flow

/** Thin [DailyMoodRepository] wrapper delegating 1:1 to the untouched [DailyMoodDao]. */
class RoomDailyMoodRepository(private val dao: DailyMoodDao) : DailyMoodRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<DailyMoodEntity>> =
        dao.observeRange(from, to)

    override suspend fun getRange(from: Long, to: Long): List<DailyMoodEntity> =
        dao.getRange(from, to)

    override suspend fun upsert(epochDay: Long, moodValue: Int, note: String?) =
        dao.upsert(DailyMoodEntity(epochDay = epochDay, moodValue = moodValue, note = note))
}
