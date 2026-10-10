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

@RunWith(AndroidJUnit4::class)
class DefaultGroupStateDaoTest {
    private lateinit var database: AffirmityDatabase
    private lateinit var dao: DefaultGroupStateDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AffirmityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.defaultGroupStateDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun noRowsByDefault() = runBlocking {
        assertTrue(dao.observeAll().first().isEmpty())
        assertTrue(dao.getAll().isEmpty())
    }

    @Test
    fun enable_createsRowEnabledAndBumpsLastUsed_onRepeat() = runBlocking {
        dao.enable("default.morning_start", nowMillis = 100L)
        assertEquals(DefaultGroupStateEntity("default.morning_start", true, 100L), dao.getAll().single())

        dao.enable("default.morning_start", nowMillis = 900L)
        assertEquals(DefaultGroupStateEntity("default.morning_start", true, 900L), dao.getAll().single())
    }

    @Test
    fun disable_flipsEnabledKeepsLastUsed_andIsNoOpWithoutRow() = runBlocking {
        dao.disable("default.everyday_essentials")
        assertTrue(dao.getAll().isEmpty())

        dao.enable("default.everyday_essentials", nowMillis = 50L)
        dao.disable("default.everyday_essentials")

        assertEquals(DefaultGroupStateEntity("default.everyday_essentials", false, 50L), dao.getAll().single())
    }
}
