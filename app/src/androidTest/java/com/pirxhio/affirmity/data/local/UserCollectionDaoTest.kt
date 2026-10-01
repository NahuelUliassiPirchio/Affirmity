package com.pirxhio.affirmity.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserCollectionDaoTest {
    private lateinit var database: AffirmityDatabase
    private lateinit var dao: UserCollectionDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AffirmityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.userCollectionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun collection(id: String, created: Long = 100L, lastUsed: Long = 0L, enabled: Boolean = false) =
        UserCollectionEntity(id, "Name $id", created, enabled, lastUsed)

    @Test
    fun deleteById_cascadesItemsOfThatCollectionOnly() = runBlocking {
        dao.insertCollection(collection("a"))
        dao.insertCollection(collection("b"))
        dao.insertItem(UserCollectionItemEntity("a", "aff-1", 1L))
        dao.insertItem(UserCollectionItemEntity("a", "aff-2", 2L))
        dao.insertItem(UserCollectionItemEntity("b", "aff-1", 3L))

        dao.deleteById("a")

        val remaining = dao.getAll()
        assertEquals(listOf("b"), remaining.map { it.collection.id })
        assertEquals(listOf("aff-1"), remaining.single().affirmationIds)
    }

    @Test
    fun enable_setsEnabledAndBumpsLastUsed_disableLeavesLastUsedUntouched() = runBlocking {
        dao.insertCollection(collection("a", lastUsed = 5L))

        dao.enable("a", nowMillis = 900L)
        val enabled = dao.getAll().single().collection
        assertTrue(enabled.enabled)
        assertEquals(900L, enabled.lastUsedAtMillis)

        dao.disable("a")
        val disabled = dao.getAll().single().collection
        assertFalse(disabled.enabled)
        assertEquals(900L, disabled.lastUsedAtMillis)
    }

    @Test
    fun observeAll_ordersByLastUsedDescThenCreatedDesc() = runBlocking {
        dao.insertCollection(collection("old-unused", created = 100L, lastUsed = 0L))
        dao.insertCollection(collection("new-unused", created = 200L, lastUsed = 0L))
        dao.insertCollection(collection("used", created = 50L, lastUsed = 700L))

        assertEquals(
            listOf("used", "new-unused", "old-unused"),
            dao.observeAll().first().map { it.collection.id },
        )
    }

    @Test
    fun observeAll_breaksFullTiesById() = runBlocking {
        dao.insertCollection(collection("b", created = 100L, lastUsed = 0L))
        dao.insertCollection(collection("a", created = 100L, lastUsed = 0L))

        assertEquals(listOf("a", "b"), dao.observeAll().first().map { it.collection.id })
    }

    @Test
    fun createWithItem_insertsCollectionAndItemTogether() = runBlocking {
        dao.createWithItem(collection("a"), UserCollectionItemEntity("a", "aff-1", 1L))

        val all = dao.getAll()
        assertEquals(listOf("a"), all.map { it.collection.id })
        assertEquals(listOf("aff-1"), all.single().affirmationIds)
    }

    @Test
    fun createWithItem_rollsBackCollectionWhenItemInsertFails() = runBlocking {
        // The item points at a different, non-existent collection: the FK rejects it and the
        // transaction must roll the collection insert back.
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                dao.createWithItem(collection("a"), UserCollectionItemEntity("missing", "aff-1", 1L))
            }
        }

        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun insertItem_duplicateIsIgnoredAndKeepsOriginalTimestamp() = runBlocking {
        dao.insertCollection(collection("a"))
        dao.insertItem(UserCollectionItemEntity("a", "aff-1", 1L))
        dao.insertItem(UserCollectionItemEntity("a", "aff-1", 99L))

        assertEquals(listOf("aff-1"), dao.getAll().single().affirmationIds)
        database.openHelper.readableDatabase
            .query("SELECT addedAtMillis FROM user_collection_items").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
                assertEquals(1, cursor.count)
            }
    }

    @Test
    fun deleteItemsForAffirmations_removesAcrossCollections() = runBlocking {
        dao.insertCollection(collection("a"))
        dao.insertCollection(collection("b"))
        dao.insertItem(UserCollectionItemEntity("a", "aff-1", 1L))
        dao.insertItem(UserCollectionItemEntity("a", "aff-2", 1L))
        dao.insertItem(UserCollectionItemEntity("b", "aff-1", 1L))
        dao.insertItem(UserCollectionItemEntity("b", "aff-3", 1L))

        dao.deleteItemsForAffirmations(listOf("aff-1", "aff-2"))

        val byId = dao.getAll().associate { it.collection.id to it.affirmationIds }
        assertEquals(emptyList<String>(), byId.getValue("a"))
        assertEquals(listOf("aff-3"), byId.getValue("b"))
    }

    @Test
    fun renameAndDeleteItem_updateRows() = runBlocking {
        dao.insertCollection(collection("a"))
        dao.insertItem(UserCollectionItemEntity("a", "aff-1", 1L))

        dao.rename("a", "Renamed")
        dao.deleteItem("a", "aff-1")

        val result = dao.getAll().single()
        assertEquals("Renamed", result.collection.name)
        assertTrue(result.affirmationIds.isEmpty())
    }
}
