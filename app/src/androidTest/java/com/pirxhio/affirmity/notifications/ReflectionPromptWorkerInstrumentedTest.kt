package com.pirxhio.affirmity.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.NotificationPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Covers task 4.2: [ReflectionPromptWorker] posts a question drawn from [ReflectionPrompts]. */
@RunWith(AndroidJUnit4::class)
class ReflectionPromptWorkerInstrumentedTest {

    private lateinit var context: Context
    private lateinit var preferences: NotificationPreferences
    private lateinit var debugLog: NotificationDebugLog

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        preferences = NotificationPreferences(context)
        debugLog = NotificationDebugLog(context)
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        runBlocking { preferences.setEnabled(NotificationChannelSpec.REFLECTION, true) }
    }

    @Test
    fun postsQuestionFromBank() {
        val worker = TestListenableWorkerBuilder<ReflectionPromptWorker>(context)
            .setWorkerFactory(object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters,
                ): ReflectionPromptWorker = ReflectionPromptWorker(
                    appContext,
                    workerParameters,
                    preferences,
                    NotificationScheduler(appContext, preferences, debugLog),
                    Notifier(appContext, debugLog),
                    debugLog,
                )
            })
            .build()

        val result = runBlocking { worker.doWork() }

        assertEquals(ListenableWorker.Result.success(), result)
        val manager = context.getSystemService(NotificationManager::class.java)
        val posted = manager.activeNotifications.firstOrNull {
            it.id == NotificationChannelSpec.REFLECTION.notificationId
        }
        val body = posted?.notification?.extras?.getCharSequence("android.text").toString()
        assertTrue(body in ReflectionPrompts.questions)
    }
}
