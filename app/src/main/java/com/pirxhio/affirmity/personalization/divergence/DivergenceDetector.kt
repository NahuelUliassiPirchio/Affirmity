package com.pirxhio.affirmity.personalization.divergence

import com.pirxhio.affirmity.personalization.scoring.ScoringWeights
import com.pirxhio.affirmity.personalization.signal.PersonalizationSignal
import java.util.concurrent.TimeUnit

/** Plain dismissal data supplied to [DivergenceDetector]; it owns no persistence mechanism. */
data class DivergenceDismissalHistory(
    val lastDismissedAtMillis: Long? = null,
    val countsByGoalId: Map<String, Int> = emptyMap(),
)

/**
 * Pure D10 decision boundary. It can only return a suggestion id: there is deliberately no store,
 * Android, Room, DataStore, or other I/O dependency through which it could change user goals.
 */
object DivergenceDetector {
    private const val STRONG_SIGNAL_WEIGHT = 3
    private const val PERMANENT_DISMISSAL_COUNT = 2
    private const val ROLLING_QUARTER_DAYS = 90

    fun suggestGoalId(
        signals: List<PersonalizationSignal>,
        themeScores: Map<String, Double>,
        declaredGoalIds: Set<String>,
        goalThemes: Map<String, Set<String>>,
        earliestSignalAtMillis: Long?,
        nowMillis: Long,
        dismissalHistory: DivergenceDismissalHistory,
        lastPromptAtMillis: Long?,
        weights: ScoringWeights = ScoringWeights.Default,
    ): String? {
        if (declaredGoalIds.isEmpty()) return null
        if (!hasElapsed(earliestSignalAtMillis, nowMillis, DivergenceThresholds.MIN_DAYS_OF_DATA)) return null
        // Storage only tracks a single lastPromptAtMillis (one slot), which is only a correct
        // encoding of "at most MAX_PROMPTS_PER_QUARTER" when that constant is 1. If it's ever
        // raised, this check -- and the storage shape behind lastPromptAtMillis -- both need to
        // track multiple timestamps, not just this comparison.
        check(DivergenceThresholds.MAX_PROMPTS_PER_QUARTER == 1) {
            "MAX_PROMPTS_PER_QUARTER > 1 requires tracking multiple prompt timestamps, not just one"
        }
        if (wasPromptedWithin(lastPromptAtMillis, nowMillis, ROLLING_QUARTER_DAYS)) return null
        if (wasPromptedWithin(
                dismissalHistory.lastDismissedAtMillis,
                nowMillis,
                DivergenceThresholds.DISMISS_COOLDOWN_DAYS,
            )
        ) return null

        // Only weigh declared goals GoalCatalog still recognizes -- a stale/retired goal id
        // silently sitting in DataStore must never zero out the safety threshold below (an
        // unrecognized goal has no mapped themes, so an unguarded minOf would let ANY candidate
        // clearing just MIN_STRONG_SIGNALS through). If none of the declared goals are recognized,
        // there's no baseline to compare against, so refuse to suggest anything rather than guess.
        val recognizedDeclaredGoalIds = declaredGoalIds.filter { it in goalThemes }
        if (recognizedDeclaredGoalIds.isEmpty()) return null

        val weakestDeclaredScore = recognizedDeclaredGoalIds.minOf { goalId ->
            aggregateThemeScore(goalThemes[goalId].orEmpty(), themeScores)
        }
        val minimumCandidateScore = weakestDeclaredScore * DivergenceThresholds.SCORE_RATIO_VS_WEAKEST_GOAL

        return goalThemes.asSequence()
            .filter { (goalId, _) -> goalId !in declaredGoalIds }
            .filter { (goalId, _) ->
                dismissalHistory.countsByGoalId.getOrDefault(goalId, 0) < PERMANENT_DISMISSAL_COUNT
            }
            .mapNotNull { (goalId, mappedThemes) ->
                val strongSignalCount = signals.count { signal ->
                    weights.weightFor(signal.type) >= STRONG_SIGNAL_WEIGHT &&
                        signal.themeId?.let(mappedThemes::contains) == true
                }
                val score = aggregateThemeScore(mappedThemes, themeScores)
                goalId.takeIf {
                    strongSignalCount >= DivergenceThresholds.MIN_STRONG_SIGNALS &&
                        score >= minimumCandidateScore
                }?.let { Candidate(goalId, score) }
            }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.goalId })
            .take(DivergenceThresholds.MAX_SUGGESTIONS_SHOWN)
            .firstOrNull()
            ?.goalId
    }

    private fun aggregateThemeScore(
        themeIds: Set<String>,
        themeScores: Map<String, Double>,
    ): Double = themeIds.sumOf { themeScores[it] ?: 0.0 }

    private fun hasElapsed(timestampMillis: Long?, nowMillis: Long, days: Int): Boolean =
        timestampMillis != null && nowMillis - timestampMillis >= TimeUnit.DAYS.toMillis(days.toLong())

    private fun wasPromptedWithin(timestampMillis: Long?, nowMillis: Long, days: Int): Boolean =
        timestampMillis != null && nowMillis - timestampMillis < TimeUnit.DAYS.toMillis(days.toLong())

    private data class Candidate(val goalId: String, val score: Double)
}
