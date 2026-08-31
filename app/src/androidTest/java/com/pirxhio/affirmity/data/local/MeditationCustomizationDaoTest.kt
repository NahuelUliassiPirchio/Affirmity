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

/**
 * [ENVIRONMENT-BLOCKED] Covers [MeditationCustomizationDao] CRUD (spec:
 * meditation-customization), following [AdUnlockDaoTest]'s in-memory-database convention.
 * Execution requires a connected device/emulator (`connectedDebugAndroidTest`), unavailable in
 * this sandbox; verified by code review against the mirrored [CatalogOverrideEntity]/
 * [CatalogOverrideDao] pair instead.
 */
@RunWith(AndroidJUnit4::class)
class MeditationCustomizationDaoTest {

    private lateinit var db: AffirmityDatabase
    private lateinit var dao: MeditationCustomizationDao

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AffirmityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.meditationCustomizationDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getById_forAnUnknownMeditation_returnsNull() = runBlocking {
        assertNull(dao.getById("box_breathing"))
    }

    @Test
    fun upsert_thenGetById_returnsTheStoredValues() = runBlocking {
        val entity = MeditationCustomizationEntity(
            meditationId = "box_breathing",
            values = mapOf("rounds" to "12", "inhaleSeconds" to "5"),
        )

        dao.upsert(entity)

        assertEquals(entity, dao.getById("box_breathing"))
    }

    @Test
    fun upsert_twiceForTheSameId_replacesRatherThanDuplicating() = runBlocking {
        dao.upsert(MeditationCustomizationEntity("box_breathing", mapOf("rounds" to "6")))
        dao.upsert(MeditationCustomizationEntity("box_breathing", mapOf("rounds" to "12")))

        assertEquals(mapOf("rounds" to "12"), dao.getById("box_breathing")?.values)
    }
}
