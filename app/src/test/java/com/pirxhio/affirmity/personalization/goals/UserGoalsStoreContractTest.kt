package com.pirxhio.affirmity.personalization.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserGoalsStoreContractTest {

    @Test
    fun `onboarding answers carry goals tone and usage moments without Android context`() {
        val answers = OnboardingAnswers(
            goalIds = setOf("calm", "direction"),
            preferredTone = "gentle",
            usageMoments = setOf("morning", "stressful_moments"),
        )

        assertEquals(setOf("calm", "direction"), answers.goalIds)
        assertEquals("gentle", answers.preferredTone)
        assertEquals(setOf("morning", "stressful_moments"), answers.usageMoments)
        assertTrue(UserGoalsStore::class.java.isAssignableFrom(UserGoalsPreferences::class.java))
    }
}
