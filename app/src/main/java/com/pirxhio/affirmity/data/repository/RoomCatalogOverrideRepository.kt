package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.CatalogOverrideDao
import com.pirxhio.affirmity.data.local.CatalogOverrideEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Thin [CatalogOverrideRepository] wrapper delegating 1:1 to [CatalogOverrideDao]. */
class RoomCatalogOverrideRepository(private val dao: CatalogOverrideDao) : CatalogOverrideRepository {
    override fun observeAll(): Flow<Map<String, Map<String, String>>> =
        dao.observeAll().map { rows -> rows.associate { it.catalogAffirmationId to it.overrides } }

    override suspend fun setOverrides(catalogAffirmationId: String, overrides: Map<String, String>) {
        val sanitized = overrides.filterValues { it.isNotBlank() }
        if (sanitized.isEmpty()) {
            dao.deleteById(catalogAffirmationId)
        } else {
            dao.upsert(CatalogOverrideEntity(catalogAffirmationId, sanitized))
        }
    }
}
