package com.pirxhio.affirmity.data

import com.pirxhio.affirmity.data.local.ActivityDailyFactDao
import com.pirxhio.affirmity.data.local.ActivityDailyFactEntity
import com.pirxhio.affirmity.data.repository.ScopedRoomDailyMoodRepository
import com.pirxhio.affirmity.data.repository.ScopedRoomDailyCompletionRepository
import com.pirxhio.affirmity.data.repository.ScopedRoomStreakHealerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class ScopedDailyRepositoriesTest {
    @Test fun `mood only completion only and healer days retain distinct facts for bound owner`() = runTest {
        val dao = Mockito.mock(ActivityDailyFactDao::class.java)
        val facts = listOf(ActivityDailyFactEntity("local:A", 100, 4, "Mood only"),
            ActivityDailyFactEntity("local:A", 101, completionPresent = true),
            ActivityDailyFactEntity("local:A", 102, healedAtMillis = 1_000))
        Mockito.`when`(dao.observeRange("local:A", 100, 102)).thenReturn(flowOf(facts))
        val moods = ScopedRoomDailyMoodRepository(dao, "local:A").observeRange(100, 102).first()
        val completions = ScopedRoomDailyCompletionRepository(dao, "local:A").observeRange(100, 102).first()
        val healers = ScopedRoomStreakHealerRepository(dao, "local:A").observeRange(100, 102).first()
        assertEquals(listOf(100L), moods.map { it.epochDay })
        assertEquals("Mood only", moods.single().note)
        assertEquals(listOf(101L), completions.map { it.epochDay })
        assertEquals(false, completions.single().meditationDone)
        assertEquals(listOf(102L), healers.map { it.healedEpochDay })
        assertEquals(1_000L, healers.single().activatedAtMillis)
    }
    @Test fun `new generation with no scoped rows never falls back to legacy archive`() = runTest {
        val dao = Mockito.mock(ActivityDailyFactDao::class.java)
        Mockito.`when`(dao.observeRange("local:B", 100, 102)).thenReturn(flowOf(emptyList()))
        assertEquals(emptyList<Any>(), ScopedRoomDailyMoodRepository(dao, "local:B").observeRange(100, 102).first())
        assertEquals(emptyList<Any>(), ScopedRoomDailyCompletionRepository(dao, "local:B").observeRange(100, 102).first())
        assertEquals(emptyList<Any>(), ScopedRoomStreakHealerRepository(dao, "local:B").observeRange(100, 102).first())
    }
}
