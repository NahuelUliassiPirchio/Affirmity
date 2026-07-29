package com.pirxhio.affirmity.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.pirxhio.affirmity.data.local.AffirmationImageStore
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.data.local.NotificationPreferences
import com.pirxhio.affirmity.data.local.TrackerPreferences
import com.pirxhio.affirmity.notifications.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Approval test for task 2.4 / spec scenario "Broken streak still shows earlier completions":
 * completing Monday, missing Tuesday, and completing Wednesday again must leave Monday and
 * Wednesday both marked completed in the derived [WeeklyStreak] — the bug this change fixes.
 */
@RunWith(AndroidJUnit4::class)
class AffirmityAppStateInstrumentedTest {

    private lateinit var db: AffirmityDatabase
    private lateinit var state: AffirmityAppState

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, AffirmityDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val notificationPreferences = NotificationPreferences(context)
        state = AffirmityAppState(
            scope = CoroutineScope(Dispatchers.Unconfined),
            affirmationDao = db.affirmationDao(),
            dailyCompletionDao = db.dailyCompletionDao(),
            trackerPreferences = TrackerPreferences(context),
            imageStore = AffirmationImageStore(context.applicationContext),
            notificationPreferences = notificationPreferences,
            notificationScheduler = NotificationScheduler(context.applicationContext, notificationPreferences),
            widgetUpdater = WidgetUpdater { },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun brokenMeditationStreak_stillShowsMondayAndWednesdayCompleted() = runBlocking {
        val monday = DayClock.weekStartEpochDay()
        val wednesday = monday + 2

        db.dailyCompletionDao().markMeditation(monday)
        // Tuesday intentionally left unmarked.
        db.dailyCompletionDao().markMeditation(wednesday)

        // Let the state's observeRange collector process the DAO writes.
        delay(200)

        assertEquals(true, state.meditationStreak.value.completedDays[0]) // Monday
        assertEquals(false, state.meditationStreak.value.completedDays[1]) // Tuesday
        assertEquals(true, state.meditationStreak.value.completedDays[2]) // Wednesday
    }
}
