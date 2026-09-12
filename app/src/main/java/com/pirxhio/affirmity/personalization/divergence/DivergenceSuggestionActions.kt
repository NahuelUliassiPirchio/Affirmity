package com.pirxhio.affirmity.personalization.divergence

import com.pirxhio.affirmity.personalization.goals.UserGoalsStore

/** Reuses the same explicit write boundary as the My Goals confirmation action. */
suspend fun addDivergenceSuggestedGoal(
    store: UserGoalsStore,
    currentGoalIds: Set<String>,
    suggestedGoalId: String,
) {
    store.saveGoalIds(currentGoalIds + suggestedGoalId)
}

/** Persists dismissal metadata only; [UserGoalsStore.saveGoalIds] is intentionally unreachable. */
suspend fun dismissDivergenceSuggestion(
    store: UserGoalsStore,
    suggestedGoalId: String,
    atMillis: Long,
) {
    store.recordDivergenceDismissal(suggestedGoalId, atMillis)
}
