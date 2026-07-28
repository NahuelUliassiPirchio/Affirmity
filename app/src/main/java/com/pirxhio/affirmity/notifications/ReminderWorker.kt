package com.pirxhio.affirmity.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.local.AffirmationDao
import com.pirxhio.affirmity.data.local.NotificationPreferences
import kotlinx.coroutines.flow.first

/**
 * Reminders channel: prompts the user to meditate and/or view affirmations, optionally quoting a
 * random saved affirmation. Self-reschedules the next occurrence before finishing, unless the
 * channel has since been disabled — in which case the chain dies here.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
    private val affirmationDao: AffirmationDao,
    private val preferences: NotificationPreferences,
    private val scheduler: NotificationScheduler,
    private val notifier: Notifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val channel = NotificationChannelSpec.REMINDER
        if (!preferences.isEnabled(channel).first()) return Result.success()

        return runCatching {
            val body = affirmationDao.randomAffirmation()?.title
                ?: applicationContext.getString(R.string.notification_reminder_fallback_body)
            notifier.notify(
                channel = channel,
                title = applicationContext.getString(R.string.notification_reminder_title),
                body = body,
            )
            scheduler.scheduleNext(channel)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
