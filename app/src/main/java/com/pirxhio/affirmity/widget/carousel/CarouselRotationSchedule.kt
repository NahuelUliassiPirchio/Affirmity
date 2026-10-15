package com.pirxhio.affirmity.widget.carousel

import androidx.work.ExistingPeriodicWorkPolicy

/** Scheduling constants for [CarouselRotationWorker]. */
object CarouselRotationSchedule {
    /** WorkManager's minimum periodic interval; exact timing under Doze is not guaranteed. */
    const val INTERVAL_MINUTES = 15L

    const val UNIQUE_WORK_NAME = "affirmation_carousel_rotation"

    /** Repeated enables must not stack or restart the schedule. */
    val EXISTING_POLICY = ExistingPeriodicWorkPolicy.KEEP
}
