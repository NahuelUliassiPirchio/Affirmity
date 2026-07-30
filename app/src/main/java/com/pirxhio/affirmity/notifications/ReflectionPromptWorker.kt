package com.pirxhio.affirmity.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pirxhio.affirmity.R
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.NotificationLogEvent
import com.pirxhio.affirmity.data.local.NotificationPreferences
import kotlinx.coroutines.flow.first

/**
 * Reflection Prompts channel: fully independent of Reminders — its own enabled flag, window, and
 * a random question from the bundled [ReflectionPrompts] bank as the body.
 */
class ReflectionPromptWorker(
    context: Context,
    params: WorkerParameters,
    private val preferences: NotificationPreferences,
    private val scheduler: NotificationScheduler,
    private val notifier: Notifier,
    private val debugLog: NotificationDebugLog,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val channel = NotificationChannelSpec.REFLECTION
        debugLog.record(channel, NotificationLogEvent.WORKER_STARTED)
        if (!preferences.isEnabled(channel).first()) {
            debugLog.record(channel, NotificationLogEvent.SKIPPED_CHANNEL_DISABLED)
            return Result.success()
        }

        return runCatching {
            notifier.notify(
                channel = channel,
                title = applicationContext.getString(R.string.notification_reflection_title),
                body = ReflectionPrompts.random(),
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
