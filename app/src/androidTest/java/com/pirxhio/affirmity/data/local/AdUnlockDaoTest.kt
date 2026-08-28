package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ENVIRONMENT-BLOCKED] Covers task C.10: CRUD + [OnConflictStrategy.IGNORE] create-if-absent
 * behavior for [AdUnlockDao] (design §4a). Written and committed to drive C.3/C.4's implementation,
 * following [DailyCompletionDaoTest]'s in-memory-database convention. Execution requires a
 * connected device/emulator (`connectedDebugAndroidTest`), unavailable in this sandbox.
 *
 * Also covers task 1.9 (design D16): [TimedAdUnlockDao.upsert] (REPLACE) is asserted here
 * alongside [AdUnlockDao.insertIfAbsent] (IGNORE) so the exact-inverse conflict behavior of the
 * two SEPARATE stores is provable in one suite.
 */
@RunWith(AndroidJUnit4::class)
class AdUnlockDaoTest {

    private lateinit var db: AffirmityDatabase
    private lateinit var dao: AdUnlockDao
    private lateinit var timedDao: TimedAdUnlockDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AffirmityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.adUnlockDao()
        timedDao = db.timedAdUnlockDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertIfAbsent_thenGetAll_returnsTheInsertedRow() = runBlocking {
        val entity = AdUnlockEntity(
            contentKey = "affirmationGroup_fuerza_de_voluntad",
            contentType = "affirmationGroup",
            contentId = "fuerza_de_voluntad",
            grantedAtMillis = 1_000L,
            expiresAtMillis = null,
        )

        dao.insertIfAbsent(entity)

        val rows = dao.getAll()
        assertEquals(1, rows.size)
        assertEquals(entity, rows[0])
    }

    @Test
    fun insertIfAbsent_secondCallForSameKey_isIgnoredNotOverwritten() = runBlocking {
        val original = AdUnlockEntity(
            contentKey = "meditation_calma",
            contentType = "meditation",
            contentId = "calma",
            grantedAtMillis = 1_000L,
            expiresAtMillis = null,
        )
        val laterAttempt = original.copy(grantedAtMillis = 5_000L)

        dao.insertIfAbsent(original)
        dao.insertIfAbsent(laterAttempt)

        val rows = dao.getAll()
        assertEquals(1, rows.size)
        assertEquals(1_000L, rows[0].grantedAtMillis)
    }

    @Test
    fun observeAll_emitsCurrentRowsAfterInsert() = runBlocking {
        val entity = AdUnlockEntity(
            contentKey = "affirmationGroup_fuerza_de_voluntad",
            contentType = "affirmationGroup",
            contentId = "fuerza_de_voluntad",
            grantedAtMillis = 2_000L,
            expiresAtMillis = 9_000L,
        )

        dao.insertIfAbsent(entity)

        val rows = dao.observeAll().first { it.isNotEmpty() }
        assertEquals(1, rows.size)
        assertEquals(entity, rows[0])
    }

    @Test
    fun getAll_emptyDatabase_returnsEmptyList() = runBlocking {
        assertTrue(dao.getAll().isEmpty())
    }

    // --- TimedAdUnlockDao: exact inverse conflict behavior (design D16, task 1.9) --------------

    @Test
    fun timedUpsert_secondCallForSameKey_REPLACES_theExactInverseOfInsertIfAbsent() = runBlocking {
        val original = TimedAdUnlockEntity(
            contentKey = "affirmationGroup_fuerza_de_voluntad",
            contentType = "affirmationGroup",
            contentId = "fuerza_de_voluntad",
            grantedAtMillis = 1_000L,
            expiresAtMillis = 5_000L,
        )
        val reEarned = original.copy(grantedAtMillis = 10_000L, expiresAtMillis = 14_000L)

        timedDao.upsert(original)
        timedDao.upsert(reEarned)

        val rows = timedDao.getAll()
        assertEquals(1, rows.size)
        assertEquals(
            "a second upsert for the same contentKey must REPLACE, unlike AdUnlockDao.insertIfAbsent",
            reEarned,
            rows[0],
        )
    }

    @Test
    fun timedObserveAll_emitsCurrentRowsAfterUpsert() = runBlocking {
        val entity = TimedAdUnlockEntity(
            contentKey = "meditation_calma",
            contentType = "meditation",
            contentId = "calma",
            grantedAtMillis = 2_000L,
            expiresAtMillis = 9_000L,
        )

        timedDao.upsert(entity)

        val rows = timedDao.observeAll().first { it.isNotEmpty() }
        assertEquals(1, rows.size)
        assertEquals(entity, rows[0])
    }

    @Test
    fun timedGetAll_emptyDatabase_returnsEmptyList() = runBlocking {
        assertTrue(timedDao.getAll().isEmpty())
    }

    @Test
    fun adUnlockAndTimedAdUnlock_areIndependentStores_writingOneNeverAffectsTheOther() = runBlocking {
        val trial = AdUnlockEntity(
            contentKey = "meditation_dormir",
            contentType = "meditation",
            contentId = "dormir",
            grantedAtMillis = 1_000L,
            expiresAtMillis = null,
        )
        val timed = TimedAdUnlockEntity(
            contentKey = "meditation_dormir",
            contentType = "meditation",
            contentId = "dormir",
            grantedAtMillis = 2_000L,
            expiresAtMillis = 6_000L,
        )

        dao.insertIfAbsent(trial)
        timedDao.upsert(timed)

        assertEquals(1, dao.getAll().size)
        assertEquals(1, timedDao.getAll().size)
        assertEquals(trial, dao.getAll()[0])
        assertEquals(timed, timedDao.getAll()[0])
    }
}
