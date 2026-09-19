package com.pirxhio.affirmity.personalization.goals

import com.pirxhio.affirmity.personalization.divergence.DivergenceDetector
import com.pirxhio.affirmity.personalization.divergence.DivergenceDismissalHistory
import com.pirxhio.affirmity.personalization.divergence.addDivergenceSuggestedGoal
import com.pirxhio.affirmity.personalization.divergence.dismissDivergenceSuggestion
import com.pirxhio.affirmity.personalization.scoring.PersonalizationScoring
import com.pirxhio.affirmity.personalization.signal.PersonalizationSignal
import com.pirxhio.affirmity.personalization.signal.SignalType
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalWriteBoundaryTest {

    private val now = 1_800_000_000_000L
    private val goalThemes = mapOf(
        "calm" to setOf("calm.theme"),
        "motivation" to setOf("motivation.theme"),
    )

    @Test
    fun `inference and dismissal never write goals`() = runTest {
        val store = RecordingUserGoalsStore(initialGoalIds = setOf("calm"))
        val signals = divergentSignals()

        val profile = PersonalizationScoring.profile(
            signals = signals,
            declaredGoalIds = store.observeGoalIds().first().orEmpty(),
            goalThemes = goalThemes,
            nowMillis = now,
        )
        val suggestion = suggest(store, signals, profile.themeScores)

        // Proves inference actually ran and produced a divergent suggestion.
        assertEquals("motivation", suggestion)

        dismissDivergenceSuggestion(store, suggestedGoalId = suggestion!!, atMillis = now)

        assertEquals(0, store.goalWrites)
        assertEquals(setOf("calm"), store.observeGoalIds().first())
    }

    @Test
    fun `accepting a suggestion writes exactly once with current plus suggested goals`() = runTest {
        val store = RecordingUserGoalsStore(initialGoalIds = setOf("calm"))
        val signals = divergentSignals()
        val profile = PersonalizationScoring.profile(signals, setOf("calm"), goalThemes, now)
        val suggestion = suggest(store, signals, profile.themeScores)

        assertEquals("motivation", suggestion)
        assertEquals(0, store.goalWrites)

        addDivergenceSuggestedGoal(store, setOf("calm"), suggestion!!)

        assertEquals(1, store.goalWrites)
        assertEquals(setOf("calm", "motivation"), store.observeGoalIds().first())
    }

    private suspend fun suggest(
        store: UserGoalsStore,
        signals: List<PersonalizationSignal>,
        themeScores: Map<String, Double>,
    ): String? = DivergenceDetector.suggestGoalId(
        signals = signals,
        themeScores = themeScores,
        declaredGoalIds = store.observeGoalIds().first().orEmpty(),
        goalThemes = goalThemes,
        earliestSignalAtMillis = now - TimeUnit.DAYS.toMillis(14),
        nowMillis = now,
        dismissalHistory = DivergenceDismissalHistory(),
        lastPromptAtMillis = null,
    )

    // Enough strong signals on an undeclared goal's theme to leave cold start and beat the ratio.
    private fun divergentSignals(): List<PersonalizationSignal> = List(12) {
        PersonalizationSignal(
            type = SignalType.AFFIRMATION_SHARED,
            themeId = "motivation.theme",
            groupId = null,
            tone = null,
            occurredAtMillis = now,
        )
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

    override fun observeDivergenceState(): Flow<DivergenceState> = MutableStateFlow(DivergenceState())

    override suspend fun saveGoalIds(ids: Set<String>) {
        goalWrites++
        goals.value = ids
    }

    override suspend fun saveOnboardingAnswers(answers: OnboardingAnswers) {
        goals.value = answers.goalIds
    }

    override suspend fun recordDivergencePromptShown(atMillis: Long) = Unit

    override suspend fun recordDivergenceDismissal(goalId: String, atMillis: Long) = Unit
}
