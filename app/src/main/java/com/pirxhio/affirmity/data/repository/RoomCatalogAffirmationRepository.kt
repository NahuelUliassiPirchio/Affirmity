package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.CatalogAffirmationDao
import com.pirxhio.affirmity.data.local.CatalogAffirmationEntity
import kotlinx.coroutines.flow.Flow

/** Thin [CatalogAffirmationRepository] wrapper, 1:1 delegation to [CatalogAffirmationDao] (design
 * D9). No mapping layer -- unlike the owned-affirmation repositories, the catalog cache has no
 * Room-vs-Firestore split (it is not per-user), so there is exactly one implementation. */
class RoomCatalogAffirmationRepository(private val dao: CatalogAffirmationDao) : CatalogAffirmationRepository {

    override fun observeByGroupIds(groupIds: Set<String>): Flow<List<CatalogAffirmationEntity>> =
        dao.observeByGroupIds(groupIds)

    override suspend fun getByIds(ids: List<String>): List<CatalogAffirmationEntity> =
        dao.getByIds(ids)
}
