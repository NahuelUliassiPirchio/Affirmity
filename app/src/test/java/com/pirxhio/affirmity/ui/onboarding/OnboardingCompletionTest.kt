package com.pirxhio.affirmity.ui.onboarding

import com.pirxhio.affirmity.personalization.goals.OnboardingAnswers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OnboardingCompletionTest {
    @Test
    fun `survey answers are persisted before the onboarding finishes`() = runTest {
        val events = mutableListOf<String>()
        var persistedAnswers: OnboardingAnswers? = null
        val allowPersistenceToFinish = CompletableDeferred<Unit>()
        val answers = mapOf(
            "goals" to setOf("calm", "direction"),
            "tone" to setOf("soft"),
            "moments" to setOf("before_sleep"),
        )

        val completion = launch {
            completeSurvey(answers, {
                persistedAnswers = it
                events += "persistence-started"
                allowPersistenceToFinish.await()
                events += "persistence-finished"
            }, { events += "finished" })
        }

        testScheduler.runCurrent()
        assertEquals(listOf("persistence-started"), events)
        allowPersistenceToFinish.complete(Unit)
        completion.join()

        assertEquals(setOf("calm", "direction"), persistedAnswers?.goalIds)
        assertEquals("soft", persistedAnswers?.preferredTone)
        assertEquals(setOf("before_sleep"), persistedAnswers?.usageMoments)
        assertEquals(listOf("persistence-started", "persistence-finished", "finished"), events)
    }

    @Test
    fun `persistence failure never finishes onboarding`() = runTest {
        var finished = false

        runCatching {
            completeSurvey(emptyMap(), { error("save failed") }, { finished = true })
        }

        assertFalse(finished)
    }
}
