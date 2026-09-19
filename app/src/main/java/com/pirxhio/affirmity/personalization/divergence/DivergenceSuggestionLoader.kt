package com.pirxhio.affirmity.personalization.divergence

import com.pirxhio.affirmity.data.local.PersonalizationSignalDao
import com.pirxhio.affirmity.personalization.goals.GoalCatalog
import com.pirxhio.affirmity.personalization.goals.UserGoalsStore
import com.pirxhio.affirmity.personalization.loadPersonalizationInputs
import com.pirxhio.affirmity.personalization.scoring.PersonalizationScoring
import kotlinx.coroutines.flow.first

/** The minimum UI state needed for an explicit add action. */
data class DivergenceSuggestion(
    val suggestedGoalId: String,
    val currentGoalIds: Set<String>,
)

/**
 * One-shot I/O boundary for divergence inputs, sharing [loadPersonalizationInputs] with Slice 5's
 * profile loader (same Room-entity-to-domain-signal mapping, one place to change it) rather than
 * re-deriving it independently.
 */
suspend fun loadDivergenceSuggestion(
    signalDao: PersonalizationSignalDao,
    userGoalsStore: UserGoalsStore,
    nowMillis: Long = System.currentTimeMillis(),
): DivergenceSuggestion? {
    val inputs = loadPersonalizationInputs(signalDao, userGoalsStore)
    val divergenceState = userGoalsStore.observeDivergenceState().first()
    val profile = PersonalizationScoring.profile(
        signals = inputs.signals,
        declaredGoalIds = inputs.declaredGoalIds,
        goalThemes = GoalCatalog.themeIdsByGoalId,
        nowMillis = nowMillis,
    )
    val suggestedGoalId = DivergenceDetector.suggestGoalId(
        signals = inputs.signals,
        themeScores = profile.themeScores,
        declaredGoalIds = inputs.declaredGoalIds,
        goalThemes = GoalCatalog.themeIdsByGoalId,
        earliestSignalAtMillis = inputs.signals.minOfOrNull { it.occurredAtMillis },
        nowMillis = nowMillis,
        dismissalHistory = DivergenceDismissalHistory(
            lastDismissedAtMillis = divergenceState.lastDismissedAtMillis,
            countsByGoalId = divergenceState.dismissalCountsByGoalId,
        ),
        lastPromptAtMillis = divergenceState.lastPromptAtMillis,
    ) ?: return null

    return DivergenceSuggestion(suggestedGoalId, inputs.declaredGoalIds)
}
