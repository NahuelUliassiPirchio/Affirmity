package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only projections the carousel widget uses (no text columns, one-shot suspend reads). */
@RunWith(AndroidJUnit4::class)
class CarouselProjectionDaoTest {
    private lateinit var database: AffirmityDatabase
    private lateinit var catalog: CatalogAffirmationDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AffirmityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        catalog = database.catalogAffirmationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun row(id: String, groupId: String, collectionId: String, sortOrder: Int = 0) =
        CatalogAffirmationEntity(
            id = id,
            text = "text $id",
            subtitle = "sub $id",
            groupId = groupId,
            themeId = "$groupId.theme",
            collectionId = collectionId,
            sortOrder = sortOrder,
        )

    @Test
    fun getRefsByGroupId_returnsOnlyThatGroupInFeedOrder() = runBlocking {
        catalog.insertAll(
            listOf(
                row("b", "g1", "c1", sortOrder = 2),
                row("a", "g1", "c1", sortOrder = 1),
                row("z", "g2", "c2"),
            ),
        )

        assertEquals(
            listOf(CatalogAffirmationRef("a", "g1", "c1"), CatalogAffirmationRef("b", "g1", "c1")),
            catalog.getRefsByGroupId("g1"),
        )
    }

    @Test
    fun getAllRefs_returnsEveryRow() = runBlocking {
        catalog.insertAll(listOf(row("a", "g1", "c1"), row("b", "g2", "c2")))

        assertEquals(setOf("a", "b"), catalog.getAllRefs().map { it.id }.toSet())
    }

    @Test
    fun getById_returnsTheFullRowOrNull() = runBlocking {
        catalog.insertAll(listOf(row("a", "g1", "c1")))

        assertEquals("text a", catalog.getById("a")?.text)
        assertNull(catalog.getById("missing"))
    }

    @Test
    fun affirmationDao_getAll_returnsOwnedRowsInInsertionOrder() = runBlocking {
        val dao = database.affirmationDao()
        dao.insert(AffirmationEntity("o1", "one", "", "color", "0"))
        dao.insert(AffirmationEntity("o2", "two", "", "color", "0"))

        assertEquals(listOf("o1", "o2"), dao.getAll().map { it.id })
    }

    @Test
    fun favoriteDao_getFavoriteIds_isNewestFirst() = runBlocking {
        val dao = database.favoriteAffirmationDao()
        dao.insert(FavoriteAffirmationEntity("old", favoritedAtMillis = 1))
        dao.insert(FavoriteAffirmationEntity("new", favoritedAtMillis = 2))

        assertEquals(listOf("new", "old"), dao.getFavoriteIds())
    }
}
