package com.pirxhio.affirmity.personalization

import com.pirxhio.affirmity.data.local.PersonalizationSignalDao
import com.pirxhio.affirmity.data.local.PersonalizationSignalEntity
import com.pirxhio.affirmity.personalization.goals.GoalCatalog
import com.pirxhio.affirmity.personalization.goals.UserGoalsStore
import com.pirxhio.affirmity.personalization.scoring.PersonalizationProfile
import com.pirxhio.affirmity.personalization.scoring.PersonalizationScoring
import com.pirxhio.affirmity.personalization.signal.PersonalizationSignal
import com.pirxhio.affirmity.personalization.signal.SignalType
import kotlinx.coroutines.flow.first

/**
 * One-shot I/O boundary that assembles the pure scoring engine's inputs from device-local stores.
 * Keeping this outside [PersonalizationScoring] preserves the scoring module's no-I/O contract.
 */
suspend fun loadPersonalizationProfile(
    signalDao: PersonalizationSignalDao,
    userGoalsStore: UserGoalsStore,
    nowMillis: Long = System.currentTimeMillis(),
): PersonalizationProfile = PersonalizationScoring.profile(
    signals = signalDao.getAll().map { it.toPersonalizationSignal() },
    declaredGoalIds = userGoalsStore.observeGoalIds().first().orEmpty(),
    goalThemes = GoalCatalog.themeIdsByGoalId,
    nowMillis = nowMillis,
)

private fun PersonalizationSignalEntity.toPersonalizationSignal() = PersonalizationSignal(
    type = SignalType.valueOf(signalType),
    themeId = themeId,
    groupId = groupId,
    tone = tone,
    occurredAtMillis = occurredAtMillis,
)
