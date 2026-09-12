package com.pirxhio.affirmity.personalization.scoring

import com.pirxhio.affirmity.personalization.signal.PersonalizationSignal
import java.util.concurrent.TimeUnit

/**
 * Pure scoring engine (spec personalization-signals "Pure scoring function", design D4). No I/O,
 * no side effects, no Android dependency (no `Context`, no Room type, no Compose) -- everything
 * needed is passed in, everything produced is plain data.
 *
 * This is what structurally guarantees the cross-slice invariant "goals never silently overwritten
 * by inference" (spec user-goals): this object has no store handle at all, so it is not merely
 * disciplined about not writing `goalIds` -- it is physically incapable of doing so.
 */
object PersonalizationScoring {

    /** A signal counts as "significant" toward the cold-start threshold when its base weight is
     * at least this value (spec "Cold start ranking": "~10 significant interactions"). */
    private const val SIGNIFICANT_WEIGHT_THRESHOLD = 3

    /** Below this many significant interactions, ranking is driven primarily by declared
     * goals/tone rather than inferred signals (spec "Cold start ranking"). */
    private const val COLD_START_THRESHOLD = 10

    /** Score added per goal-mapped theme while cold: large enough to dominate any signal noise a
     * pre-threshold user could realistically accumulate. */
    private const val COLD_START_GOAL_BOOST = 100.0

    /** Score added per goal-mapped theme once warm: present "alongside" inferred signals (spec
     * "Transition past cold start"), not overriding them. */
    private const val WARM_GOAL_BOOST = 5.0

    fun profile(
        signals: List<PersonalizationSignal>,
        declaredGoalIds: Set<String>,
        goalThemes: Map<String, Set<String>>,
        nowMillis: Long,
        weights: ScoringWeights = ScoringWeights.Default,
    ): PersonalizationProfile {
        val significantSignalCount = signals.count { weights.weightFor(it.type) >= SIGNIFICANT_WEIGHT_THRESHOLD }
        val isColdStart = significantSignalCount < COLD_START_THRESHOLD

        val themeScores = mutableMapOf<String, Double>()
        val universeScores = mutableMapOf<String, Double>()
        val toneScores = mutableMapOf<String, Double>()

        for (signal in signals) {
            val ageDays = TimeUnit.MILLISECONDS.toDays(nowMillis - signal.occurredAtMillis).coerceAtLeast(0)
            val decayed = weights.weightFor(signal.type) * DecayBands.multiplierForAgeDays(ageDays)

            signal.themeId?.let { themeId ->
                themeScores[themeId] = (themeScores[themeId] ?: 0.0) + decayed
            }
            signal.groupId?.let { groupId ->
                universeScores[groupId] = (universeScores[groupId] ?: 0.0) + decayed
            }
            signal.tone?.let { tone ->
                toneScores[tone] = (toneScores[tone] ?: 0.0) + decayed
            }
        }

        val goalBoost = if (isColdStart) COLD_START_GOAL_BOOST else WARM_GOAL_BOOST
        for (goalId in declaredGoalIds) {
            for (themeId in goalThemes[goalId].orEmpty()) {
                themeScores[themeId] = (themeScores[themeId] ?: 0.0) + goalBoost
            }
        }

        val dominantTone = toneScores.maxByOrNull { it.value }?.key

        return PersonalizationProfile(
            themeScores = themeScores,
            universeScores = universeScores,
            dominantTone = dominantTone,
            significantSignalCount = significantSignalCount,
            isColdStart = isColdStart,
        )
    }
}
