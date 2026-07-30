package com.pirxhio.affirmity.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.pirxhio.affirmity.data.local.NotificationDebugLog
import com.pirxhio.affirmity.data.local.NotificationLogEvent
import com.pirxhio.affirmity.data.local.NotificationPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Wraps [WorkManager] to (re)enqueue or cancel each channel's self-rescheduling work chains. Each
 * channel's window is split into [SLOTS_PER_DAY] equal thirds (see
 * [NotificationSchedule.subWindow]), and each slot runs its own independent unique-work chain so a
 * delayed or dropped slot never affects the other two.
 */
class NotificationScheduler(
    private val context: Context,
    private val preferences: NotificationPreferences,
    private val debugLog: NotificationDebugLog,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Enqueues any of [channel]'s slots that has nothing currently pending — safe to call on
     * every app start/relaunch without drifting or duplicating any slot's chain.
     */
    suspend fun ensureScheduled(channel: NotificationChannelSpec) {
        if (!preferences.isEnabled(channel).first()) return

        for (slot in 0 until SLOTS_PER_DAY) {
            val infos = withContext(Dispatchers.IO) {
                workManager.getWorkInfosForUniqueWork(uniqueWorkName(channel, slot)).get()
            }
            if (infos.any { !it.state.isFinished }) continue
            scheduleNext(channel, slot)
        }
    }

    /** (Re)enqueues all of [channel]'s slots — used when the channel is enabled or its window changes. */
    suspend fun scheduleNext(channel: NotificationChannelSpec) {
        for (slot in 0 until SLOTS_PER_DAY) {
            scheduleNext(channel, slot)
        }
    }

    /** Rolls a fresh random instant inside [slot]'s share of [channel]'s window and (re)enqueues it. */
    suspend fun scheduleNext(channel: NotificationChannelSpec, slot: Int) {
        val settings = preferences.observe(channel).first()
        val now = System.currentTimeMillis()
        val (slotStart, slotEnd) = NotificationSchedule.subWindow(
            startMinute = settings.startMinute,
            endMinute = settings.endMinute,
            slotIndex = slot,
            slotCount = SLOTS_PER_DAY,
        )
        val triggerAt = NotificationSchedule.nextTriggerAtMillis(
            nowMillis = now,
            startMinute = slotStart,
            endMinute = slotEnd,
        )
        val inputData = Data.Builder().putInt(KEY_SLOT_INDEX, slot).build()

        val request = when (channel) {
            NotificationChannelSpec.REMINDER -> OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(triggerAt - now, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(channel.workTag)
                .build()

            NotificationChannelSpec.REFLECTION -> OneTimeWorkRequestBuilder<ReflectionPromptWorker>()
                .setInitialDelay(triggerAt - now, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(channel.workTag)
                .build()
        }

        workManager.enqueueUniqueWork(uniqueWorkName(channel, slot), ExistingWorkPolicy.REPLACE, request)
        debugLog.record(
            channel,
            NotificationLogEvent.SCHEDULED,
            "slot $slot @ ${TIME_FORMAT.format(Date(triggerAt))}",
        )
    }

    /** Cancels all of [channel]'s slot chains; the other channel's pending work is untouched. */
    fun cancel(channel: NotificationChannelSpec) {
        for (slot in 0 until SLOTS_PER_DAY) {
            workManager.cancelUniqueWork(uniqueWorkName(channel, slot))
        }
    }

    private fun uniqueWorkName(channel: NotificationChannelSpec, slot: Int) =
        "${channel.uniqueWorkName}_slot$slot"

    companion object {
        const val SLOTS_PER_DAY = 3
        const val KEY_SLOT_INDEX = "slot_index"
        private val TIME_FORMAT = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    }
}
