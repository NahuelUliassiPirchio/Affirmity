package com.pirxhio.affirmity.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.NotificationPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers task 4.4: calling [NotificationScheduler.ensureScheduled] twice in a row must enqueue
 * exactly one pending occurrence per daily slot — the idempotent-reseed guarantee relied on for
 * app relaunch and reboot recovery.
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
        scheduler = NotificationScheduler(context, preferences, NotificationDebugLog(context))
        runBlocking { preferences.setEnabled(NotificationChannelSpec.REMINDER, true) }
    }

    @Test
    fun ensureScheduledTwice_enqueuesExactlyOnePendingOccurrencePerSlot() = runBlocking {
        scheduler.ensureScheduled(NotificationChannelSpec.REMINDER)
        scheduler.ensureScheduled(NotificationChannelSpec.REMINDER)

        for (slot in 0 until NotificationScheduler.SLOTS_PER_DAY) {
            val infos = androidx.work.WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork("${NotificationChannelSpec.REMINDER.uniqueWorkName}_slot$slot")
                .get()
            val pending = infos.filter { !it.state.isFinished }

            assertEquals("slot=$slot", 1, pending.size)
        }
    }
}
