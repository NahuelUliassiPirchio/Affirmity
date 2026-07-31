package com.pirxhio.affirmity.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Self-rescheduling one-shot worker that fires ~60s after each local midnight so the widget's
 * "today" outline advances even while the app is closed (D11). Signed-in users also get a silent
 * `day_rollover` FCM ping (see `notifications/AffirmityMessagingService.kt`); this worker is the
 * offline/signed-out fallback (fcm-notifications design.md's "Day rollover" decision) and needs no
 * custom `WorkerFactory` — WorkManager uses its default factory for it.
 */
class DayRolloverWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        WeeklyTrackerWidget().updateAll(applicationContext)
        enqueue(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "day_rollover_worker"

        fun enqueue(context: Context) {
            val delay = DayRolloverSchedule.delayUntilNextMidnightMillis(System.currentTimeMillis())
            val request = OneTimeWorkRequestBuilder<DayRolloverWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
