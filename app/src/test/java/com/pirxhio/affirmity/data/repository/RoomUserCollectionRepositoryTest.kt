package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.DEFAULT_COLLECTION_HIGHLIGHT_ID
import com.pirxhio.affirmity.data.local.UserCollectionDao
import com.pirxhio.affirmity.data.local.UserCollectionEntity
import com.pirxhio.affirmity.data.local.UserCollectionItemEntity
import com.pirxhio.affirmity.data.local.UserCollectionWithItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomUserCollectionRepositoryTest {
    private class FakeDao : UserCollectionDao {
        val createdCollections = mutableListOf<UserCollectionEntity>()
        val createdItems = mutableListOf<UserCollectionItemEntity?>()
        val deleteBatches = mutableListOf<List<String>>()

        override suspend fun createWithItem(collection: UserCollectionEntity, item: UserCollectionItemEntity?) {
            createdCollections += collection
            createdItems += item
        }

        override suspend fun deleteItemsForAffirmations(affirmationIds: Collection<String>) {
            deleteBatches += affirmationIds.toList()
        }

        override fun observeAll(): Flow<List<UserCollectionWithItems>> = emptyFlow()
        override suspend fun getAll(): List<UserCollectionWithItems> = emptyList()
        override suspend fun insertCollection(entity: UserCollectionEntity) = Unit
        override suspend fun insertItem(entity: UserCollectionItemEntity) = Unit
        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun deleteById(id: String) = Unit
        override suspend fun deleteItem(userCollectionId: String, affirmationId: String) = Unit
        override suspend fun enable(id: String, nowMillis: Long) = Unit
        override suspend fun disable(id: String) = Unit
    }

    @Test
    fun create_startsEnabledWithLastUsedEqualToCreationTime() = runBlocking {
        val dao = FakeDao()

        RoomUserCollectionRepository(dao).create("c1", "Morning", nowMillis = 1234L, initialAffirmationId = "aff-1")

        val entity = dao.createdCollections.single()
        assertTrue(entity.enabled)
        assertEquals(1234L, entity.createdAtMillis)
        assertEquals(1234L, entity.lastUsedAtMillis)
        assertEquals(UserCollectionItemEntity("c1", "aff-1", 1234L), dao.createdItems.single())
    }

    @Test
    fun create_storesTheHighlightAndDefaultsWhenOmitted() = runBlocking {
        val dao = FakeDao()
        val repository = RoomUserCollectionRepository(dao)

        repository.create("c1", "Morning", nowMillis = 1L, initialAffirmationId = null, highlightId = "gold")
        repository.create("c2", "Evening", nowMillis = 2L, initialAffirmationId = null)

        assertEquals(listOf("gold", DEFAULT_COLLECTION_HIGHLIGHT_ID), dao.createdCollections.map { it.highlightId })
    }

    @Test
    fun removeAffirmations_chunksIdsBelowSqliteVariableCap() = runBlocking {
        val dao = FakeDao()
        val ids = (1..1201).map { "aff-$it" }

        RoomUserCollectionRepository(dao).removeAffirmations(ids)

        assertTrue(dao.deleteBatches.all { it.size <= 500 })
        assertEquals(listOf(500, 500, 201), dao.deleteBatches.map { it.size })
        assertEquals(ids, dao.deleteBatches.flatten())
    }

    @Test
    fun removeAffirmations_emptyInputSkipsDao() = runBlocking {
        val dao = FakeDao()

        RoomUserCollectionRepository(dao).removeAffirmations(emptyList())

        assertTrue(dao.deleteBatches.isEmpty())
    }
}
