package com.pirxhio.affirmity.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pirxhio.affirmity.data.local.NotificationPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Wraps [WorkManager] to (re)enqueue or cancel each channel's self-rescheduling work chain. */
class NotificationScheduler(
    private val context: Context,
    private val preferences: NotificationPreferences,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Enqueues the next occurrence for [channel] only if nothing is currently pending — safe to
     * call on every app start/relaunch without drifting or duplicating the chain.
     */
    suspend fun ensureScheduled(channel: NotificationChannelSpec) {
        if (!preferences.isEnabled(channel).first()) return

        val infos = withContext(Dispatchers.IO) {
            workManager.getWorkInfosForUniqueWork(channel.uniqueWorkName).get()
        }
        if (infos.any { !it.state.isFinished }) return

        scheduleNext(channel)
    }

    /** Rolls a fresh random instant inside [channel]'s window and (re)enqueues as unique work. */
    suspend fun scheduleNext(channel: NotificationChannelSpec) {
        val settings = preferences.observe(channel).first()
        val now = System.currentTimeMillis()
        val triggerAt = NotificationSchedule.nextTriggerAtMillis(
            nowMillis = now,
            startMinute = settings.startMinute,
            endMinute = settings.endMinute,
        )

        val request = when (channel) {
            NotificationChannelSpec.REMINDER -> OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(triggerAt - now, TimeUnit.MILLISECONDS)
                .addTag(channel.workTag)
                .build()

            NotificationChannelSpec.REFLECTION -> OneTimeWorkRequestBuilder<ReflectionPromptWorker>()
                .setInitialDelay(triggerAt - now, TimeUnit.MILLISECONDS)
                .addTag(channel.workTag)
                .build()
        }

        workManager.enqueueUniqueWork(channel.uniqueWorkName, ExistingWorkPolicy.REPLACE, request)
    }

    /** Cancels only [channel]'s chain; the other channel's pending work is untouched. */
    fun cancel(channel: NotificationChannelSpec) {
        workManager.cancelUniqueWork(channel.uniqueWorkName)
    }
}
