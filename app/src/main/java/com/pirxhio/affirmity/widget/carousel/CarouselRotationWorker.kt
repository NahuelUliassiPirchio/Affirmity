package com.pirxhio.affirmity.widget.carousel

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Advances every carousel instance by one position every 15 minutes (spec R2). For each widget it
 * raises `advancePending` and requests an update; `provideGlance` consumes the flag. Instances are
 * isolated so one failure never stops the others (R10.2).
 */
class CarouselRotationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val widget = AffirmationCarouselWidget()
        val ids = GlanceAppWidgetManager(applicationContext).getGlanceIds(AffirmationCarouselWidget::class.java)
        val outcomes = CarouselRotationRunner.runAll(ids) { id ->
            GlanceCarouselStateStore(applicationContext, id).update { it.copy(advancePending = true) }
            widget.update(applicationContext, id)
        }
        outcomes.forEach { (id, outcome) ->
            if (outcome is RotationOutcome.Failed) Log.w(TAG, "Rotation failed for $id", outcome.error)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "CarouselRotation"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<CarouselRotationWorker>(
                CarouselRotationSchedule.INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                CarouselRotationSchedule.UNIQUE_WORK_NAME,
                CarouselRotationSchedule.EXISTING_POLICY,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(CarouselRotationSchedule.UNIQUE_WORK_NAME)
        }
    }
}
