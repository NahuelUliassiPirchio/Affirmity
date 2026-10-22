package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.ActivityDao
import com.pirxhio.affirmity.data.local.stored
import com.pirxhio.affirmity.data.repository.RoomActivityRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class RoomActivityRepositoryTest {
    private val event = ActivityEvent("local:G", "view", 20_000, ActivityKind.VIEW,
        100, 100, 100, "UTC", null, null, 0, "device")

    @Test fun `local record returns durable state and range retains actual view`() = runTest {
        val dao = Mockito.mock(ActivityDao::class.java)
        Mockito.`when`(dao.record(event.stored("LOCAL"))).thenReturn(event.stored("LOCAL"))
        Mockito.`when`(dao.observeRange("local:G", 20_000, 20_001)).thenReturn(flowOf(listOf(event.stored("LOCAL"))))
        Mockito.`when`(dao.observeCoverage("local:G", 20_000, 20_001)).thenReturn(flowOf(emptyList()))
        val repo = RoomActivityRepository(dao, "local:G")
        assertEquals(ActivitySaveState.LOCAL, repo.record(event))
        val range = repo.observeRange("local:G", 20_000, 20_001).toList().last() as ActivityRangeState.Ready
        assertEquals(1, range.days.getValue(20_000).viewedCount)
        assertEquals(ActivityCoverage.UNKNOWN, range.days.getValue(20_001).coverage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `local repository refuses a different generation rather than redirecting`() = runTest {
        RoomActivityRepository(Mockito.mock(ActivityDao::class.java), "local:H").record(event)
        Unit
    }
}
