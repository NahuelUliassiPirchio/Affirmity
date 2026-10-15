package com.pirxhio.affirmity.widget.carousel

import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class CarouselRotationScheduleTest {

    @Test
    fun `rotation runs every 15 minutes, the WorkManager minimum`() {
        assertEquals(15L, CarouselRotationSchedule.INTERVAL_MINUTES)
        assertEquals(TimeUnit.MINUTES.toMillis(15), CarouselRotationSchedule.intervalMillis)
    }

    @Test
    fun `work is unique and an existing schedule is kept`() {
        assertEquals("affirmation_carousel_rotation", CarouselRotationSchedule.UNIQUE_WORK_NAME)
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, CarouselRotationSchedule.EXISTING_POLICY)
    }
}
