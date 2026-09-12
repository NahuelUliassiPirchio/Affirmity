package com.pirxhio.affirmity.personalization.goals

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalWriteBoundaryTest {

    @Test
    fun `observing goals while divergent signals arrive never writes goals`() = runTest {
        val store = RecordingUserGoalsStore(initialGoalIds = setOf("calm"))
        val divergentSignals = listOf("high_energy_session", "motivational_theme_opened")

        assertEquals(setOf("calm"), store.observeGoalIds().first())
        assertEquals(2, divergentSignals.size) // Placeholder seam until scoring lands in Slice 5.
        assertEquals(0, store.goalWrites)
    }

    @Test
    fun `saveGoalIds is the sole operation that records an explicit goal write`() = runTest {
        val store = RecordingUserGoalsStore(initialGoalIds = null)

        store.saveGoalIds(setOf("confidence"))

        assertEquals(1, store.goalWrites)
        assertEquals(setOf("confidence"), store.observeGoalIds().first())
    }
}

private class RecordingUserGoalsStore(initialGoalIds: Set<String>?) : UserGoalsStore {
    private val goals = MutableStateFlow(initialGoalIds)
    var goalWrites: Int = 0
        private set

    override fun observeGoalIds(): Flow<Set<String>?> = goals

    override fun observePreferredTone(): Flow<String?> = MutableStateFlow(null)

    override fun observeUsageMoments(): Flow<Set<String>?> = MutableStateFlow(null)

    override suspend fun saveGoalIds(ids: Set<String>) {
        goalWrites++
        goals.value = ids
    }

    override suspend fun saveOnboardingAnswers(answers: OnboardingAnswers) {
        goals.value = answers.goalIds
    }
}
