package com.pirxhio.affirmity.personalization.goals

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userGoalsDataStore by preferencesDataStore(name = "user_goals_prefs")

/**
 * Device-local goal preferences. Deliberately not part of DataSession or Firestore sync, and not
 * cleared on sign-out, matching [com.pirxhio.affirmity.data.local.AffirmationThemePreferences].
 */
class UserGoalsPreferences(private val context: Context) : UserGoalsStore {

    override fun observeGoalIds(): Flow<Set<String>?> =
        context.userGoalsDataStore.data.map { it[GOAL_IDS] }

    override fun observePreferredTone(): Flow<String?> =
        context.userGoalsDataStore.data.map { it[PREFERRED_TONE] }

    override fun observeUsageMoments(): Flow<Set<String>?> =
        context.userGoalsDataStore.data.map { it[USAGE_MOMENTS] }

    override fun observeDivergenceState(): Flow<DivergenceState> =
        context.userGoalsDataStore.data.map { preferences ->
            DivergenceState(
                lastPromptAtMillis = preferences[DIVERGENCE_LAST_PROMPT_AT],
                lastDismissedAtMillis = preferences[DIVERGENCE_LAST_DISMISSED_AT],
                dismissalCountsByGoalId = divergenceDismissalCounts(
                    dismissedOnceGoalIds = preferences[DIVERGENCE_DISMISSED_GOAL_IDS].orEmpty(),
                    dismissedTwiceGoalIds = preferences[DIVERGENCE_DISMISSED_TWICE_GOAL_IDS].orEmpty(),
                ),
            )
        }

    override suspend fun saveGoalIds(ids: Set<String>) {
        context.userGoalsDataStore.edit { it[GOAL_IDS] = ids }
    }

    override suspend fun saveOnboardingAnswers(answers: OnboardingAnswers) {
        context.userGoalsDataStore.edit { preferences ->
            preferences[GOAL_IDS] = answers.goalIds
            answers.preferredTone?.let { preferences[PREFERRED_TONE] = it }
                ?: preferences.remove(PREFERRED_TONE)
            answers.usageMoments?.let { preferences[USAGE_MOMENTS] = it }
                ?: preferences.remove(USAGE_MOMENTS)
        }
    }

    override suspend fun recordDivergencePromptShown(atMillis: Long) {
        context.userGoalsDataStore.edit { it[DIVERGENCE_LAST_PROMPT_AT] = atMillis }
    }

    override suspend fun recordDivergenceDismissal(goalId: String, atMillis: Long) {
        context.userGoalsDataStore.edit { preferences ->
            val tiers = nextDivergenceDismissalTiers(
                goalId = goalId,
                dismissedOnceGoalIds = preferences[DIVERGENCE_DISMISSED_GOAL_IDS].orEmpty(),
                dismissedTwiceGoalIds = preferences[DIVERGENCE_DISMISSED_TWICE_GOAL_IDS].orEmpty(),
            )
            preferences[DIVERGENCE_DISMISSED_GOAL_IDS] = tiers.onceGoalIds
            preferences[DIVERGENCE_DISMISSED_TWICE_GOAL_IDS] = tiers.twiceGoalIds
            preferences[DIVERGENCE_LAST_DISMISSED_AT] = atMillis
        }
    }

    private companion object {
        val GOAL_IDS = stringSetPreferencesKey("goal_ids")
        val PREFERRED_TONE = stringPreferencesKey("preferred_tone")
        val USAGE_MOMENTS = stringSetPreferencesKey("usage_moments")

        val DIVERGENCE_LAST_PROMPT_AT = longPreferencesKey("divergence_last_prompt_at")
        val DIVERGENCE_DISMISSED_GOAL_IDS = stringSetPreferencesKey("divergence_dismissed_goal_ids")
        val DIVERGENCE_LAST_DISMISSED_AT = longPreferencesKey("divergence_last_dismissed_at")
        val DIVERGENCE_DISMISSED_TWICE_GOAL_IDS =
            stringSetPreferencesKey("divergence_dismissed_twice_goal_ids")
    }
}

internal data class DivergenceDismissalTiers(
    val onceGoalIds: Set<String>,
    val twiceGoalIds: Set<String>,
)

/**
 * Two string sets are the smallest DataStore-native representation of the only counts the policy
 * distinguishes (one and two-or-more). It avoids a JSON/map codec and caps permanent dismissals at
 * two while retaining the Slice 3 reserved "dismissed at least once" key.
 */
internal fun divergenceDismissalCounts(
    dismissedOnceGoalIds: Set<String>,
    dismissedTwiceGoalIds: Set<String>,
): Map<String, Int> = (dismissedOnceGoalIds + dismissedTwiceGoalIds).associateWith { goalId ->
    if (goalId in dismissedTwiceGoalIds) 2 else 1
}

internal fun nextDivergenceDismissalTiers(
    goalId: String,
    dismissedOnceGoalIds: Set<String>,
    dismissedTwiceGoalIds: Set<String>,
): DivergenceDismissalTiers = if (goalId in dismissedOnceGoalIds) {
    DivergenceDismissalTiers(
        onceGoalIds = dismissedOnceGoalIds,
        twiceGoalIds = dismissedTwiceGoalIds + goalId,
    )
} else {
    DivergenceDismissalTiers(
        onceGoalIds = dismissedOnceGoalIds + goalId,
        twiceGoalIds = dismissedTwiceGoalIds,
    )
}
