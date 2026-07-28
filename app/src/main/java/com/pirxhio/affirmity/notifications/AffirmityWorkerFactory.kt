package com.pirxhio.affirmity.notifications

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.pirxhio.affirmity.data.local.AffirmationDao
import com.pirxhio.affirmity.data.local.NotificationPreferences

/**
 * Hand-written `WorkerFactory` (no DI framework, matching the project's existing style) so
 * [ReminderWorker] and [ReflectionPromptWorker] can receive their dependencies explicitly instead
 * of reaching for `AffirmityDatabase.getInstance(context)` directly — this is what lets
 * `TestListenableWorkerBuilder` inject fakes in instrumented tests.
 */
class AffirmityWorkerFactory(
    private val affirmationDao: AffirmationDao,
    private val preferences: NotificationPreferences,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        val scheduler = NotificationScheduler(appContext, preferences)
        val notifier = Notifier(appContext)
        return when (workerClassName) {
            ReminderWorker::class.java.name -> ReminderWorker(
                appContext,
                workerParameters,
                affirmationDao,
                preferences,
                scheduler,
                notifier,
            )

            ReflectionPromptWorker::class.java.name -> ReflectionPromptWorker(
                appContext,
                workerParameters,
                preferences,
                scheduler,
                notifier,
            )

            else -> null
        }
    }
}
