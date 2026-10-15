package com.pirxhio.affirmity.widget.carousel

import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit

/** Scheduling constants for [CarouselRotationWorker] (spec R2). */
object CarouselRotationSchedule {
    /** WorkManager's minimum periodic interval; exact timing under Doze is not guaranteed (R2.2). */
    const val INTERVAL_MINUTES = 15L
    val intervalMillis: Long = TimeUnit.MINUTES.toMillis(INTERVAL_MINUTES)

    const val UNIQUE_WORK_NAME = "affirmation_carousel_rotation"

    /** Repeated enables must not stack or restart the schedule (R2.1). */
    val EXISTING_POLICY = ExistingPeriodicWorkPolicy.KEEP
}
