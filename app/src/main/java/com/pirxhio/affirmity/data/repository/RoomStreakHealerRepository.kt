package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.StreakHealerUseDao
import com.pirxhio.affirmity.data.local.StreakHealerUseEntity
import kotlinx.coroutines.flow.Flow

/** Thin [StreakHealerRepository] wrapper delegating 1:1 to the untouched [StreakHealerUseDao]. */
class RoomStreakHealerRepository(private val dao: StreakHealerUseDao) : StreakHealerRepository {
    override fun observeRange(from: Long, to: Long): Flow<List<StreakHealerUseEntity>> =
        dao.observeRange(from, to)

    override suspend fun getRange(from: Long, to: Long): List<StreakHealerUseEntity> =
        dao.getRange(from, to)

    override suspend fun recordUse(healedEpochDay: Long) = dao.upsert(
        StreakHealerUseEntity(healedEpochDay = healedEpochDay, activatedAtMillis = System.currentTimeMillis())
    )
}
