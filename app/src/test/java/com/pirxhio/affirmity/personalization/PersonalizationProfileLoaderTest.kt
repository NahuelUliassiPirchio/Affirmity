package com.pirxhio.affirmity.personalization

import com.pirxhio.affirmity.data.local.PersonalizationSignalDao
import com.pirxhio.affirmity.data.local.PersonalizationSignalEntity
import com.pirxhio.affirmity.personalization.goals.GoalCatalog
import com.pirxhio.affirmity.personalization.goals.DivergenceState
import com.pirxhio.affirmity.personalization.goals.OnboardingAnswers
import com.pirxhio.affirmity.personalization.goals.UserGoalsStore
import com.pirxhio.affirmity.personalization.scoring.PersonalizationProfile
import com.pirxhio.affirmity.personalization.signal.SignalType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PersonalizationProfileLoaderTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `loader maps persisted signals and declared goals into a scored profile`() = runTest {
        val entity = PersonalizationSignalEntity(
            signalType = SignalType.AFFIRMATION_SAVED.name,
            themeId = "observed.theme",
            groupId = "observed_universe",
            tone = "direct",
            occurredAtMillis = now,
        )
        val dao = FakeSignalDao(listOf(entity))
        val store = FakeUserGoalsStore(setOf("calm"))
        val calmTheme = GoalCatalog.themeIdsByGoalId.getValue("calm").first()

        val profile = loadPersonalizationProfile(dao, store, nowMillis = now)

        assertEquals(1, dao.readCount)
        assertEquals(5.0, profile.themeScores.getValue("observed.theme"), 0.0001)
        assertEquals(100.0, profile.themeScores.getValue(calmTheme), 0.0001)
        assertEquals(5.0, profile.universeScores.getValue("observed_universe"), 0.0001)
        assertEquals("direct", profile.dominantTone)
        assertEquals(1, profile.significantSignalCount)
        assertEquals(true, profile.isColdStart)
    }

    @Test
    fun `loader treats never-persisted goals as empty`() = runTest {
        val dao = FakeSignalDao(emptyList())
        val store = FakeUserGoalsStore(null)

        val profile = loadPersonalizationProfile(dao, store, nowMillis = now)

        assertEquals(
            PersonalizationProfile(
                themeScores = emptyMap(),
                universeScores = emptyMap(),
                dominantTone = null,
                significantSignalCount = 0,
                isColdStart = true,
            ),
            profile,
        )
        assertEquals(1, dao.readCount)
    }
}

private class FakeSignalDao(
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

private class FakeUserGoalsStore(
    private val goalIds: Set<String>?,
) : UserGoalsStore {
    override fun observeGoalIds(): Flow<Set<String>?> = flowOf(goalIds)

    override fun observePreferredTone(): Flow<String?> = flowOf(null)

    override fun observeUsageMoments(): Flow<Set<String>?> = flowOf(null)

    override fun observeDivergenceState(): Flow<DivergenceState> = flowOf(DivergenceState())

    override suspend fun saveGoalIds(ids: Set<String>) = Unit

    override suspend fun saveOnboardingAnswers(answers: OnboardingAnswers) = Unit

    override suspend fun recordDivergencePromptShown(atMillis: Long) = Unit

    override suspend fun recordDivergenceDismissal(goalId: String, atMillis: Long) = Unit
}
