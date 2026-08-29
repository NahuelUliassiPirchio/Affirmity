package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.AffirmationDao
import com.pirxhio.affirmity.data.local.AffirmationEntity
import kotlinx.coroutines.flow.Flow

/** Thin [AffirmationRepository] wrapper delegating 1:1 to the untouched [AffirmationDao]. */
class RoomAffirmationRepository(private val dao: AffirmationDao) : AffirmationRepository {
    override fun observeAll(): Flow<List<AffirmationEntity>> = dao.observeAll()
    override suspend fun insert(entity: AffirmationEntity) = dao.insert(entity)
    override suspend fun deleteById(id: String) = dao.deleteById(id)
    override suspend fun deleteAll() = dao.deleteAll()
    override suspend fun setOverrides(id: String, overrides: Map<String, String>) =
        dao.updateOverrides(id, overrides.filterValues { it.isNotBlank() })
}
