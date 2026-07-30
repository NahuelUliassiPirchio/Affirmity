package com.pirxhio.affirmity.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.local.AffirmationDao
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.NotificationLogEvent
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
    private val debugLog: NotificationDebugLog,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val channel = NotificationChannelSpec.REMINDER
        debugLog.record(channel, NotificationLogEvent.WORKER_STARTED)
        if (!preferences.isEnabled(channel).first()) {
            debugLog.record(channel, NotificationLogEvent.SKIPPED_CHANNEL_DISABLED)
            return Result.success()
        }

        return runCatching {
            val body = affirmationDao.randomAffirmation()?.title
                ?: applicationContext.getString(R.string.notification_reminder_fallback_body)
            notifier.notify(
                channel = channel,
                title = applicationContext.getString(R.string.notification_reminder_title),
                body = body,
            )
            val slot = inputData.getInt(NotificationScheduler.KEY_SLOT_INDEX, 0)
            scheduler.scheduleNext(channel, slot)
            Result.success()
        }.getOrElse {
            debugLog.record(channel, NotificationLogEvent.WORKER_FAILED, it.message.orEmpty())
            Result.retry()
        }
    }
}
