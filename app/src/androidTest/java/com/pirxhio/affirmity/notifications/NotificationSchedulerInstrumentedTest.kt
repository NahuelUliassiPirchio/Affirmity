package com.pirxhio.affirmity.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.pirxhio.affirmity.data.local.NotificationPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers task 4.4: calling [NotificationScheduler.ensureScheduled] twice in a row must enqueue
 * exactly one pending occurrence — the idempotent-reseed guarantee relied on for app relaunch and
 * reboot recovery.
 */
@RunWith(AndroidJUnit4::class)
class NotificationSchedulerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var preferences: NotificationPreferences
    private lateinit var scheduler: NotificationScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        preferences = NotificationPreferences(context)
        scheduler = NotificationScheduler(context, preferences)
        runBlocking { preferences.setEnabled(NotificationChannelSpec.REMINDER, true) }
    }

    @Test
    fun ensureScheduledTwice_enqueuesExactlyOnePendingOccurrence() = runBlocking {
        scheduler.ensureScheduled(NotificationChannelSpec.REMINDER)
        scheduler.ensureScheduled(NotificationChannelSpec.REMINDER)

        val infos = androidx.work.WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(NotificationChannelSpec.REMINDER.uniqueWorkName)
            .get()
        val pending = infos.filter { !it.state.isFinished }

        assertEquals(1, pending.size)
    }
}
