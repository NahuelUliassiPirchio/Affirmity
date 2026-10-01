package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.UserCollection
import com.pirxhio.affirmity.data.local.UserCollectionDao
import com.pirxhio.affirmity.data.local.UserCollectionEntity
import com.pirxhio.affirmity.data.local.UserCollectionItemEntity
import com.pirxhio.affirmity.data.local.UserCollectionWithItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomUserCollectionRepository(
    private val dao: UserCollectionDao,
) : UserCollectionRepository {
    override fun observeCollections(): Flow<List<UserCollection>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getCollections(): List<UserCollection> =
        dao.getAll().map { it.toDomain() }

    override suspend fun create(
        id: String,
        name: String,
        nowMillis: Long,
        initialAffirmationId: String?,
        highlightId: String,
    ) {
        dao.createWithItem(
            collection = UserCollectionEntity(
                id = id,
                name = name,
                createdAtMillis = nowMillis,
                enabled = true,
                lastUsedAtMillis = nowMillis,
                highlightId = highlightId,
            ),
            item = initialAffirmationId?.let { UserCollectionItemEntity(id, it, nowMillis) },
        )
    }

    override suspend fun rename(id: String, name: String) = dao.rename(id, name)

    override suspend fun delete(id: String) = dao.deleteById(id)

    override suspend fun addItem(userCollectionId: String, affirmationId: String, nowMillis: Long) =
        dao.insertItem(UserCollectionItemEntity(userCollectionId, affirmationId, nowMillis))

    override suspend fun removeItem(userCollectionId: String, affirmationId: String) =
        dao.deleteItem(userCollectionId, affirmationId)

    override suspend fun enable(id: String, nowMillis: Long) = dao.enable(id, nowMillis)

    override suspend fun disable(id: String) = dao.disable(id)

    override suspend fun removeAffirmations(affirmationIds: Collection<String>) {
        if (affirmationIds.isEmpty()) return
        affirmationIds.chunked(MAX_BOUND_VARIABLES).forEach { dao.deleteItemsForAffirmations(it) }
    }
}

/** Well under SQLite's 999 bound-variable cap on minSdk 24. */
private const val MAX_BOUND_VARIABLES = 500

private fun UserCollectionWithItems.toDomain() = UserCollection(
    id = collection.id,
    name = collection.name,
    createdAtMillis = collection.createdAtMillis,
    enabled = collection.enabled,
    lastUsedAtMillis = collection.lastUsedAtMillis,
    affirmationIds = affirmationIds,
    highlightId = collection.highlightId,
)
