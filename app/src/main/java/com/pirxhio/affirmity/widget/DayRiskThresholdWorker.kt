package com.pirxhio.affirmity.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Self-rescheduling one-shot worker that re-renders the widget just after the 20:00 local
 * end-of-day threshold so the band appears without the app being opened. Modeled on
 * [DayRolloverWorker]; midnight rollover (which clears the band) stays there.
 */
class DayRiskThresholdWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            WeeklyTrackerWidget().updateAll(applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never let a render failure break the reschedule chain below.
            Log.w(TAG, "widget update failed; rescheduling anyway", e)
        }
        // Re-arm only AFTER updateAll finished: REPLACE from inside doWork cancels this running
        // work, so nothing suspending may follow. KEEP would be a no-op (this run is still active).
        enqueue(applicationContext, ExistingWorkPolicy.REPLACE)
        // Also ensure the sibling job so one worker's chain heals the other.
        DayRolloverWorker.ensureScheduled(applicationContext)
        return Result.success()
    }

    companion object {
        private const val TAG = "DayRiskThresholdWorker"
        private const val UNIQUE_WORK_NAME = "day_risk_threshold_worker"

        /** Idempotent: leaves an already-pending run untouched. Safe from app start / onUpdate. */
        fun ensureScheduled(context: Context) = enqueue(context, ExistingWorkPolicy.KEEP)

        fun enqueue(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
            val delay = DayRolloverSchedule.delayUntilNextThresholdMillis(System.currentTimeMillis())
            val request = OneTimeWorkRequestBuilder<DayRiskThresholdWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, policy, request)
        }

        /**
         * For clock / time-zone changes: recompute the delay for the current zone. Replaces only
         * when no run is RUNNING, because REPLACE would cancel an in-flight `updateAll`; a running
         * worker re-arms itself with the fresh zone as soon as it finishes.
         */
        suspend fun rescheduleForClockChange(context: Context) {
            val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(UNIQUE_WORK_NAME).first()
            if (infos.none { it.state == WorkInfo.State.RUNNING }) enqueue(context, ExistingWorkPolicy.REPLACE)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
