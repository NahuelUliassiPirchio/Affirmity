package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteAffirmationDaoTest {
    private lateinit var database: AffirmityDatabase
    private lateinit var dao: FavoriteAffirmationDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AffirmityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.favoriteAffirmationDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertObserveAndDelete_roundTripsFavoriteState() = runBlocking {
        dao.insert(FavoriteAffirmationEntity("affirmation-1", favoritedAtMillis = 100L))

        assertEquals(listOf("affirmation-1"), dao.observeFavoriteIds().first())
        assertTrue(dao.isFavorite("affirmation-1"))

        dao.deleteById("affirmation-1")

        assertEquals(emptyList<String>(), dao.observeFavoriteIds().first())
        assertFalse(dao.isFavorite("affirmation-1"))
    }

    @Test
    fun observeFavoriteIds_ordersMostRecentFirst() = runBlocking {
        dao.insert(FavoriteAffirmationEntity("oldest", favoritedAtMillis = 100L))
        dao.insert(FavoriteAffirmationEntity("newest", favoritedAtMillis = 300L))
        dao.insert(FavoriteAffirmationEntity("middle", favoritedAtMillis = 200L))

        assertEquals(
            listOf("newest", "middle", "oldest"),
            dao.observeFavoriteIds().first(),
        )
    }

    @Test
    fun insertForExistingId_replacesTimestampAndRefreshesRecency() = runBlocking {
        dao.insert(FavoriteAffirmationEntity("first", favoritedAtMillis = 100L))
        dao.insert(FavoriteAffirmationEntity("second", favoritedAtMillis = 200L))

        dao.insert(FavoriteAffirmationEntity("first", favoritedAtMillis = 300L))

        assertEquals(listOf("first", "second"), dao.observeFavoriteIds().first())
    }
}
