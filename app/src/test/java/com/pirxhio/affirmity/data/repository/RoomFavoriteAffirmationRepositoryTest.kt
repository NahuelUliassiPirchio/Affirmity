package com.pirxhio.affirmity.data.repository

import com.pirxhio.affirmity.data.local.FavoriteAffirmationDao
import com.pirxhio.affirmity.data.local.FavoriteAffirmationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RoomFavoriteAffirmationRepositoryTest {
    @Test
    fun `add maps id and timestamp to entity`() = runBlocking {
        val dao = FakeFavoriteAffirmationDao()
        val repository = RoomFavoriteAffirmationRepository(dao)

        repository.add("affirmation-1", favoritedAtMillis = 123L)

        assertEquals(FavoriteAffirmationEntity("affirmation-1", 123L), dao.insertedEntity)
    }

    @Test
    fun `remove delegates id to dao`() = runBlocking {
        val dao = FakeFavoriteAffirmationDao()
        val repository = RoomFavoriteAffirmationRepository(dao)

        repository.remove("affirmation-1")

        assertEquals("affirmation-1", dao.deletedId)
    }

    @Test
    fun `clear delegates to dao`() = runBlocking {
        val dao = FakeFavoriteAffirmationDao()
        val repository = RoomFavoriteAffirmationRepository(dao)

        repository.clear()

        assertEquals(1, dao.deleteAllCalls)
    }

    @Test
    fun `isFavorite returns dao result`() = runBlocking {
        val dao = FakeFavoriteAffirmationDao(isFavoriteResult = false)
        val repository = RoomFavoriteAffirmationRepository(dao)

        assertFalse(repository.isFavorite("missing"))
        assertEquals("missing", dao.isFavoriteId)
    }

    @Test
    fun `observeFavoriteIds emits the ids supplied by the dao`() = runBlocking {
        val ids = flowOf(listOf("newest", "oldest"))
        val dao = FakeFavoriteAffirmationDao(observedIds = ids)
        val repository = RoomFavoriteAffirmationRepository(dao)

        assertEquals(listOf("newest", "oldest"), repository.observeFavoriteIds().first())
    }

    private class FakeFavoriteAffirmationDao(
        private val observedIds: Flow<List<String>> = flowOf(emptyList()),
        private val isFavoriteResult: Boolean = true,
    ) : FavoriteAffirmationDao {
        var insertedEntity: FavoriteAffirmationEntity? = null
        var deletedId: String? = null
        var deleteAllCalls: Int = 0
        var isFavoriteId: String? = null

        override suspend fun insert(entity: FavoriteAffirmationEntity) {
            insertedEntity = entity
        }

        override suspend fun deleteById(id: String) {
            deletedId = id
        }

        override suspend fun deleteAll() {
            deleteAllCalls += 1
        }

        override fun observeFavoriteIds(): Flow<List<String>> = observedIds

        override suspend fun isFavorite(id: String): Boolean {
            isFavoriteId = id
            return isFavoriteResult
        }
    }
}
