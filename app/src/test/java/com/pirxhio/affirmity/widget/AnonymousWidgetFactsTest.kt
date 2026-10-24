package com.pirxhio.affirmity.widget

import com.pirxhio.affirmity.data.local.ActivityDailyFactDao
import com.pirxhio.affirmity.data.local.ActivityDailyFactEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class AnonymousWidgetFactsTest {
    @Test fun `reads completions and healer uses from the owner scoped facts`() = runTest {
        val dao = mock<ActivityDailyFactDao> {
            onBlocking { getRange("local:A", 90, 100) } doReturn listOf(
                ActivityDailyFactEntity("local:A", 98, completionPresent = true, meditationDone = true, affirmationDone = true),
                ActivityDailyFactEntity("local:A", 99, healedAtMillis = 5_000),
            )
            onBlocking { getRange("local:A", 95, 100) } doReturn listOf(
                ActivityDailyFactEntity("local:A", 99, healedAtMillis = 5_000),
            )
            onBlocking { getAll("local:A") } doReturn listOf(
                ActivityDailyFactEntity("local:A", 98, completionPresent = true, meditationDone = true, affirmationDone = true),
            )
        }
        val facts = loadAnonymousWidgetFacts(dao, "local:A", completionsFrom = 90, healersFrom = 95, to = 100)
        assertTrue(facts.hasAny)
        assertEquals(listOf(98L), facts.completions.map { it.epochDay })
        assertEquals(true, facts.completions.single().affirmationDone)
        assertEquals(listOf(99L), facts.healerUses.map { it.healedEpochDay })
        assertEquals(5_000L, facts.healerUses.single().activatedAtMillis)
    }

    @Test fun `a fresh owner without completion facts reports nothing even if healer or mood rows exist`() = runTest {
        val dao = mock<ActivityDailyFactDao> {
            onBlocking { getRange("local:B", 90, 100) } doReturn listOf(ActivityDailyFactEntity("local:B", 99, 3, "mood only"))
            onBlocking { getRange("local:B", 95, 100) } doReturn emptyList()
            onBlocking { getAll("local:B") } doReturn listOf(ActivityDailyFactEntity("local:B", 99, 3, "mood only"))
        }
        val facts = loadAnonymousWidgetFacts(dao, "local:B", completionsFrom = 90, healersFrom = 95, to = 100)
        assertFalse(facts.hasAny)
        assertEquals(emptyList<Long>(), facts.completions.map { it.epochDay })
    }
}
