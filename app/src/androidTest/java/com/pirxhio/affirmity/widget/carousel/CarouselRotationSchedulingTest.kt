package com.pirxhio.affirmity.widget.carousel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Covers S2.1, S2.2 and S2.4 with WorkManager's test driver. Emulator only. */
class CarouselRotationSchedulingTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setMinimumLoggingLevel(android.util.Log.DEBUG).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    private fun infos(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(CarouselRotationSchedule.UNIQUE_WORK_NAME).get()

    @Test
    fun enqueue_schedulesUniquePeriodicWorkEvery15Minutes() {
        CarouselRotationWorker.enqueue(context)

        val info = infos().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(TimeUnit.MINUTES.toMillis(15), info.periodicityInfo?.repeatIntervalMillis)
    }

    @Test
    fun enqueueTwice_keepsExactlyOneWork() {
        CarouselRotationWorker.enqueue(context)
        val first = infos().single().id

        CarouselRotationWorker.enqueue(context)

        assertEquals(first, infos().single().id)
    }

    @Test
    fun cancel_cancelsTheWork() {
        CarouselRotationWorker.enqueue(context)

        CarouselRotationWorker.cancel(context)

        assertEquals(WorkInfo.State.CANCELLED, infos().single().state)
    }
}
