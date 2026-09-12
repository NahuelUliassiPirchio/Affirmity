package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers design D7 retention: `deleteOlderThan` (180-day age prune) and `trimToNewest` (5000-row
 * cap), the two DAO primitives `RoomPersonalizationSignalRecorder` calls opportunistically every
 * 50th insert.
 */
@RunWith(AndroidJUnit4::class)
class PersonalizationSignalDaoTest {
    private lateinit var database: AffirmityDatabase
    private lateinit var dao: PersonalizationSignalDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AffirmityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.personalizationSignalDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(occurredAtMillis: Long) = PersonalizationSignalEntity(
        signalType = "AFFIRMATION_SAVED",
        themeId = "self_worth.t1",
        groupId = "self_worth",
        tone = "powerful",
        occurredAtMillis = occurredAtMillis,
    )

    @Test
    fun deleteOlderThan_prunesOnlyRowsOlderThanCutoff() = runBlocking {
        val dayMillis = 24L * 60 * 60 * 1000
        dao.insert(entity(occurredAtMillis = 0))
        dao.insert(entity(occurredAtMillis = 100 * dayMillis))
        dao.insert(entity(occurredAtMillis = 200 * dayMillis))

        val cutoff = 180L * dayMillis
        dao.deleteOlderThan(cutoff)

        val remaining = dao.getAll()
        assertEquals(1, remaining.size)
        assertEquals(200 * dayMillis, remaining.single().occurredAtMillis)
    }

    @Test
    fun trimToNewest_keepsOnlyNewestRowsUpToLimit() = runBlocking {
        repeat(10) { index -> dao.insert(entity(occurredAtMillis = index.toLong())) }

        dao.trimToNewest(3)

        val remaining = dao.getAll().map { it.occurredAtMillis }
        assertEquals(listOf(7L, 8L, 9L), remaining)
    }
}
