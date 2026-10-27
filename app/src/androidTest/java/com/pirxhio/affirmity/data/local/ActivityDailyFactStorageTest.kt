package com.pirxhio.affirmity.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ActivityDailyFactStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.databaseBuilder(context, AffirmityDatabase::class.java, "activity-daily-fact-test").build()
    @After fun close() { db.close(); context.deleteDatabase("activity-daily-fact-test") }

    @Test fun firstGenerationAdoptsAllRetainedFactsOnlyOnceAndNewGenerationStartsEmpty() = runBlocking {
        db.dailyMoodDao().upsert(DailyMoodEntity(100, 4, "Retained"))
        db.dailyCompletionDao().markMeditation(100)
        val first = db.initializeAnonymousActivityOwner(ActivityOwnerEntity("local:first", "device", null, true))
        val adopted = db.activityDailyFactDao().getRange(first.owner, 99, 101).single()
        assertEquals(4, adopted.moodValue)
        assertEquals(true, adopted.meditationDone)
        assertEquals(true, first.legacyAdoptionComplete)
        db.activityDao().claimAnonymous(first.owner, "A")
        val second = db.initializeAnonymousActivityOwner(ActivityOwnerEntity("local:second", "device", null, true))
        assertEquals(first.owner, second.legacyAdopterOwner)
        assertEquals(emptyList<ActivityDailyFactEntity>(), db.activityDailyFactDao().getRange(second.owner, 99, 101))
        db.activityDailyFactDao().upsertMood(second.owner, 100, 2, "New generation")
        assertEquals(4, db.activityDailyFactDao().getRange(first.owner, 100, 100).single().moodValue)
        assertEquals(false, db.activityDailyFactDao().getRange(second.owner, 100, 100).single().meditationDone)
        assertEquals(4, db.dailyMoodDao().getRange(100, 100).single().moodValue)
    }

    @Test fun capturedWriteAfterClaimFailsWithoutReroutingOrChangingOriginal() = runBlocking {
        val first = db.initializeAnonymousActivityOwner(ActivityOwnerEntity("local:first", "device", null, true))
        db.activityDailyFactDao().upsertMood(first.owner, 100, 4, "Original")
        db.activityDao().claimAnonymous(first.owner, "A")
        try {
            db.activityDailyFactDao().upsertMood(first.owner, 100, 1, "Stale")
            fail("A claimed generation must not accept stale writes")
        } catch (_: IllegalArgumentException) {
            assertEquals("Original", db.activityDailyFactDao().getRange(first.owner, 100, 100).single().note)
        }
    }

    @Test fun scopedMoodCompletionAndHealerWritesPreserveOtherFields() = runBlocking {
        val owner = db.initializeAnonymousActivityOwner(ActivityOwnerEntity("local:first", "device", null, true)).owner
        val dao = db.activityDailyFactDao()
        dao.upsertMood(owner, 100, 4, "First")
        dao.markMeditation(owner, 100)
        dao.markAffirmation(owner, 100)
        dao.recordHealer(owner, 100, 1_000)
        dao.upsertMood(owner, 100, 2, "Latest")
        dao.recordHealer(owner, 100, 2_000)
        assertEquals(ActivityDailyFactEntity(owner, 100, 2, "Latest", true, true, true, 1_000), dao.getAll(owner).single())
    }
}
