package com.pirxhio.affirmity.personalization

import com.pirxhio.affirmity.data.local.PersonalizationSignalDao
import com.pirxhio.affirmity.personalization.goals.GoalCatalog
import com.pirxhio.affirmity.personalization.goals.UserGoalsStore
import com.pirxhio.affirmity.personalization.scoring.PersonalizationProfile
import com.pirxhio.affirmity.personalization.scoring.PersonalizationScoring

/**
 * One-shot I/O boundary that assembles the pure scoring engine's inputs from device-local stores.
 * Keeping this outside [PersonalizationScoring] preserves the scoring module's no-I/O contract.
 */
suspend fun loadPersonalizationProfile(
    signalDao: PersonalizationSignalDao,
    userGoalsStore: UserGoalsStore,
    nowMillis: Long = System.currentTimeMillis(),
): PersonalizationProfile {
    val inputs = loadPersonalizationInputs(signalDao, userGoalsStore)
    return PersonalizationScoring.profile(
        signals = inputs.signals,
        declaredGoalIds = inputs.declaredGoalIds,
        goalThemes = GoalCatalog.themeIdsByGoalId,
        nowMillis = nowMillis,
    )
}
