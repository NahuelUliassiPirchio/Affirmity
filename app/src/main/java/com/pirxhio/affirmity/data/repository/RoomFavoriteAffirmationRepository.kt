package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.FavoriteAffirmationDao
import com.pirxhio.affirmity.data.local.FavoriteAffirmationEntity
import kotlinx.coroutines.flow.Flow

class RoomFavoriteAffirmationRepository(
    private val dao: FavoriteAffirmationDao,
) : FavoriteAffirmationRepository {
    override fun observeFavoriteIds(): Flow<List<String>> = dao.observeFavoriteIds()

    override suspend fun isFavorite(id: String): Boolean = dao.isFavorite(id)

    override suspend fun add(id: String, favoritedAtMillis: Long) {
        dao.insert(FavoriteAffirmationEntity(id, favoritedAtMillis))
    }

    override suspend fun remove(id: String) {
        dao.deleteById(id)
    }

    override suspend fun clear() {
        dao.deleteAll()
    }
}
