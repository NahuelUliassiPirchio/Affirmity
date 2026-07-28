package com.pirxhio.affirmity.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.local.AffirmationDao
import com.pirxhio.affirmity.data.local.AffirmationEntity
import com.pirxhio.affirmity.data.local.AffirmityDatabase
import com.pirxhio.affirmity.data.local.NotificationPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers tasks 4.1 and 4.3: [ReminderWorker] posts the right body (real affirmation, or the
 * static fallback when the table is empty) and re-enqueues its unique-work chain after finishing.
 */
@RunWith(AndroidJUnit4::class)
class ReminderWorkerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var database: AffirmityDatabase
    private lateinit var preferences: NotificationPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AffirmityDatabase::class.java).build()
        preferences = NotificationPreferences(context)
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        runBlocking { preferences.setEnabled(NotificationChannelSpec.REMINDER, true) }
    }

    private fun buildWorker(): ReminderWorker =
        TestListenableWorkerBuilder<ReminderWorker>(context)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters,
                ): ListenableWorker = ReminderWorker(
                    appContext,
                    workerParameters,
                    database.affirmationDao(),
                    preferences,
                    NotificationScheduler(appContext, preferences),
                    Notifier(appContext),
                )
            })
            .build()

    @Test
    fun emptyAffirmationsTable_fallsBackToStaticBody() {
        val worker = buildWorker()

        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
        val manager = context.getSystemService(NotificationManager::class.java)
        val posted = manager.activeNotifications.firstOrNull {
            it.id == NotificationChannelSpec.REMINDER.notificationId
        }
        val expectedFallback = context.getString(R.string.notification_reminder_fallback_body)
        assertEquals(expectedFallback, posted?.notification?.extras?.getCharSequence("android.text").toString())
    }

    @Test
    fun withAffirmation_posts_andReschedules() {
        runBlocking {
            database.affirmationDao().insert(
                AffirmationEntity(
                    id = "1",
                    title = "You are enough",
                    subtitle = "",
                    backgroundType = "color",
                    backgroundValue = "#000000",
                )
            )
        }

        val worker = buildWorker()
        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success(), result)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(NotificationChannelSpec.REMINDER.uniqueWorkName)
            .get()
        assertEquals(true, infos.isNotEmpty())
    }
}
