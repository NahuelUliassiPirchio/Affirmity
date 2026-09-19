package com.pirxhio.affirmity.personalization.divergence

import com.pirxhio.affirmity.data.local.PersonalizationSignalDao
import com.pirxhio.affirmity.data.local.PersonalizationSignalEntity
import com.pirxhio.affirmity.personalization.goals.DivergenceState
import com.pirxhio.affirmity.personalization.goals.OnboardingAnswers
import com.pirxhio.affirmity.personalization.goals.UserGoalsStore
import com.pirxhio.affirmity.personalization.signal.SignalType
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DivergenceSuggestionLoaderTest {

    private val now = 1_800_000_000_000L

    @Test
    fun `loader assembles raw signals profile goals and history into one suggestion`() = runTest {
        val candidateTheme = "motivation_discipline_responsibility.discipline_consistency"
        val signals = List(12) { index ->
            PersonalizationSignalEntity(
                signalType = SignalType.AFFIRMATION_SAVED.name,
                themeId = candidateTheme,
                groupId = null,
                tone = null,
                occurredAtMillis = now - TimeUnit.DAYS.toMillis((14L + index)),
            )
        }
        val dao = DivergenceFakeSignalDao(signals)
        val store = DivergenceFakeGoalsStore(goalIds = setOf("calm"))

        val suggestion = loadDivergenceSuggestion(dao, store, now)

        assertEquals(DivergenceSuggestion("motivation", setOf("calm")), suggestion)
        assertEquals(1, dao.readCount)
    }

    @Test
    fun `loader honors persisted prompt history`() = runTest {
        val candidateTheme = "motivation_discipline_responsibility.discipline_consistency"
        val signals = List(12) {
            PersonalizationSignalEntity(
                signalType = SignalType.AFFIRMATION_SAVED.name,
                themeId = candidateTheme,
                groupId = null,
                tone = null,
                occurredAtMillis = now - TimeUnit.DAYS.toMillis(14),
            )
        }
        val store = DivergenceFakeGoalsStore(
            goalIds = setOf("calm"),
            divergenceState = DivergenceState(lastPromptAtMillis = now - TimeUnit.DAYS.toMillis(10)),
        )

        assertNull(loadDivergenceSuggestion(DivergenceFakeSignalDao(signals), store, now))
    }
}

private class DivergenceFakeSignalDao(
    private val entities: List<PersonalizationSignalEntity>,
) : PersonalizationSignalDao {
    var readCount = 0
        private set

    override suspend fun insert(entity: PersonalizationSignalEntity) = Unit

    override suspend fun getAll(): List<PersonalizationSignalEntity> {
        readCount++
        return entities
    }

    override suspend fun deleteOlderThan(cutoffMillis: Long) = Unit

    override suspend fun trimToNewest(limit: Int) = Unit
}

private class DivergenceFakeGoalsStore(
    private val goalIds: Set<String>?,
    private val divergenceState: DivergenceState = DivergenceState(),
) : UserGoalsStore {
    override fun observeGoalIds(): Flow<Set<String>?> = flowOf(goalIds)

    override fun observePreferredTone(): Flow<String?> = flowOf(null)

    override fun observeUsageMoments(): Flow<Set<String>?> = flowOf(null)

    override fun observeDivergenceState(): Flow<DivergenceState> = flowOf(divergenceState)

    override suspend fun saveGoalIds(ids: Set<String>) = Unit

    override suspend fun saveOnboardingAnswers(answers: OnboardingAnswers) = Unit

    override suspend fun recordDivergencePromptShown(atMillis: Long) = Unit

    override suspend fun recordDivergenceDismissal(goalId: String, atMillis: Long) = Unit
}
