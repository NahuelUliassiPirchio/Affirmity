package com.pirxhio.affirmity.personalization.divergence

import com.pirxhio.affirmity.personalization.goals.DivergenceState
import com.pirxhio.affirmity.personalization.goals.OnboardingAnswers
import com.pirxhio.affirmity.personalization.goals.UserGoalsStore
import com.pirxhio.affirmity.personalization.signal.PersonalizationSignal
import com.pirxhio.affirmity.personalization.signal.SignalType
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class DivergenceSuggestionActionsTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `dismiss leaves goals unchanged and cooldown prevents immediate re-show`() = runTest {
        val store = ActionRecordingGoalsStore(setOf("declared"))

        dismissDivergenceSuggestion(store, suggestedGoalId = "candidate", atMillis = now)

        assertEquals(setOf("declared"), store.goals.value)
        assertEquals(0, store.goalWrites)
        assertEquals(listOf("candidate" to now), store.dismissals)
        val state = store.divergenceState.value
        assertNull(
            DivergenceDetector.suggestGoalId(
                signals = List(5) {
                    PersonalizationSignal(
                        type = SignalType.AFFIRMATION_SAVED,
                        themeId = "candidate.theme",
                        groupId = null,
                        tone = null,
                        occurredAtMillis = now,
                    )
                },
                themeScores = mapOf("declared.theme" to 10.0, "candidate.theme" to 20.0),
                declaredGoalIds = store.goals.value.orEmpty(),
                goalThemes = mapOf(
                    "declared" to setOf("declared.theme"),
                    "candidate" to setOf("candidate.theme"),
                ),
                earliestSignalAtMillis = now - TimeUnit.DAYS.toMillis(14),
                nowMillis = now,
                dismissalHistory = DivergenceDismissalHistory(
                    lastDismissedAtMillis = state.lastDismissedAtMillis,
                    countsByGoalId = state.dismissalCountsByGoalId,
                ),
                lastPromptAtMillis = state.lastPromptAtMillis,
            ),
        )
    }

    @Test
    fun `add uses the explicit saveGoalIds path with existing and suggested goals`() = runTest {
        val store = ActionRecordingGoalsStore(setOf("calm"))

        addDivergenceSuggestedGoal(
            store = store,
            currentGoalIds = setOf("calm"),
            suggestedGoalId = "motivation",
        )

        assertEquals(1, store.goalWrites)
        assertEquals(setOf("calm", "motivation"), store.goals.value)
        assertEquals(0, store.onboardingWrites)
        assertEquals(emptyList<Pair<String, Long>>(), store.dismissals)
    }

    @Test
    fun `explicit action cancellation is never swallowed`() = runTest {
        val cancellation = CancellationException("screen stopped")
        val store = ActionRecordingGoalsStore(setOf("calm"), saveFailure = cancellation)

        try {
            addDivergenceSuggestedGoal(store, setOf("calm"), "motivation")
            fail("CancellationException must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }
}

private class ActionRecordingGoalsStore(
    initialGoals: Set<String>,
    private val saveFailure: Throwable? = null,
) : UserGoalsStore {
    val goals = MutableStateFlow<Set<String>?>(initialGoals)
    val divergenceState = MutableStateFlow(DivergenceState())
    val dismissals = mutableListOf<Pair<String, Long>>()
    var goalWrites = 0
        private set
    var onboardingWrites = 0
        private set

    override fun observeGoalIds(): Flow<Set<String>?> = goals

    override fun observePreferredTone(): Flow<String?> = MutableStateFlow(null)

    override fun observeUsageMoments(): Flow<Set<String>?> = MutableStateFlow(null)

    override fun observeDivergenceState(): Flow<DivergenceState> = divergenceState

    override suspend fun saveGoalIds(ids: Set<String>) {
        saveFailure?.let { throw it }
        goalWrites++
        goals.value = ids
    }

    override suspend fun saveOnboardingAnswers(answers: OnboardingAnswers) {
        onboardingWrites++
        goals.value = answers.goalIds
    }

    override suspend fun recordDivergencePromptShown(atMillis: Long) {
        divergenceState.value = divergenceState.value.copy(lastPromptAtMillis = atMillis)
    }

    override suspend fun recordDivergenceDismissal(goalId: String, atMillis: Long) {
        dismissals += goalId to atMillis
        val oldState = divergenceState.value
        divergenceState.value = oldState.copy(
            lastDismissedAtMillis = atMillis,
            dismissalCountsByGoalId = oldState.dismissalCountsByGoalId +
                (goalId to (oldState.dismissalCountsByGoalId.getOrDefault(goalId, 0) + 1)),
        )
    }
}
