package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers tasks 1.7/1.8: partial-day upsert must not clobber the sibling flag (spec: same-day
 * double view / independent habit tracking), and `hasAny()` gates the widget's empty state.
 */
@RunWith(AndroidJUnit4::class)
class DailyCompletionDaoTest {

    private lateinit var db: AffirmityDatabase
    private lateinit var dao: DailyCompletionDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AffirmityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dailyCompletionDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun markMeditationThenAffirmation_sameDay_bothFlagsTrueOnOneRow() = runBlocking {
        val today = 200L

        dao.markMeditation(today)
        dao.markAffirmation(today)

        val rows = dao.getRange(today, today)
        assertEquals(1, rows.size)
        assertTrue(rows[0].meditationDone)
        assertTrue(rows[0].affirmationDone)
    }

    @Test
    fun markMeditationOnly_affirmationFlagStaysFalse() = runBlocking {
        val today = 201L

        dao.markMeditation(today)

        val rows = dao.getRange(today, today)
        assertEquals(1, rows.size)
        assertTrue(rows[0].meditationDone)
        assertFalse(rows[0].affirmationDone)
    }

    @Test
    fun hasAny_falseOnEmptyDatabase() = runBlocking {
        assertFalse(dao.hasAny())
    }

    @Test
    fun hasAny_trueAfterAnyMark() = runBlocking {
        dao.markAffirmation(202L)

        assertTrue(dao.hasAny())
    }
}
