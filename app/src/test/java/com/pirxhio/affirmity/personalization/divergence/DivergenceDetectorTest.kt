package com.pirxhio.affirmity.personalization.divergence

import com.pirxhio.affirmity.personalization.signal.PersonalizationSignal
import com.pirxhio.affirmity.personalization.signal.SignalType
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DivergenceDetectorTest {

    private val now = 1_800_000_000_000L
    private val goalThemes = linkedMapOf(
        "declared" to setOf("declared.theme"),
        "candidate" to setOf("candidate.theme"),
        "better_candidate" to setOf("better.theme"),
    )

    @Test
    fun `candidate qualifies exactly at age signal-count and score-ratio thresholds`() {
        val result = detect(
            signals = strongSignals("candidate.theme", count = 5),
            themeScores = mapOf("declared.theme" to 10.0, "candidate.theme" to 20.0),
            earliestSignalAtMillis = now - days(14),
        )

        assertEquals("candidate", result)
    }

    @Test
    fun `less than fourteen days of data never suggests a goal`() {
        val result = detect(
            signals = strongSignals("candidate.theme", count = 5),
            themeScores = mapOf("declared.theme" to 10.0, "candidate.theme" to 20.0),
            earliestSignalAtMillis = now - days(14) + 1,
        )

        assertNull(result)
    }

    @Test
    fun `fewer than five strong signals never suggests a goal`() {
        val signals = strongSignals("candidate.theme", count = 4) +
            signal(SignalType.SURFACE_OPENED, "candidate.theme") +
            signal(SignalType.AFFIRMATION_VIEWED, "candidate.theme")

        assertNull(
            detect(
                signals = signals,
                themeScores = mapOf("declared.theme" to 10.0, "candidate.theme" to 30.0),
            ),
        )
    }

    @Test
    fun `candidate below twice the weakest declared goal score is not suggested`() {
        assertNull(
            detect(
                signals = strongSignals("candidate.theme", count = 5),
                themeScores = mapOf("declared.theme" to 10.0, "candidate.theme" to 19.999),
            ),
        )
    }

    @Test
    fun `only the highest-scoring qualifying undeclared goal is returned`() {
        val result = detect(
            signals = strongSignals("candidate.theme", 5) + strongSignals("better.theme", 5),
            themeScores = mapOf(
                "declared.theme" to 10.0,
                "candidate.theme" to 20.0,
                "better.theme" to 40.0,
            ),
        )

        assertEquals("better_candidate", result)
    }

    @Test
    fun `a dismissal suppresses every prompt until the thirty-day cooldown expires`() {
        val qualifyingSignals = strongSignals("candidate.theme", 5)
        val scores = mapOf("declared.theme" to 10.0, "candidate.theme" to 20.0)

        assertNull(
            detect(
                signals = qualifyingSignals,
                themeScores = scores,
                dismissalHistory = DivergenceDismissalHistory(
                    lastDismissedAtMillis = now - days(30) + 1,
                    countsByGoalId = mapOf("candidate" to 1),
                ),
            ),
        )
        assertEquals(
            "candidate",
            detect(
                signals = qualifyingSignals,
                themeScores = scores,
                dismissalHistory = DivergenceDismissalHistory(
                    lastDismissedAtMillis = now - days(30),
                    countsByGoalId = mapOf("candidate" to 1),
                ),
            ),
        )
    }

    @Test
    fun `a goal dismissed twice is permanently suppressed but another goal may qualify`() {
        val result = detect(
            signals = strongSignals("candidate.theme", 5) + strongSignals("better.theme", 5),
            themeScores = mapOf(
                "declared.theme" to 10.0,
                "candidate.theme" to 50.0,
                "better.theme" to 30.0,
            ),
            dismissalHistory = DivergenceDismissalHistory(
                lastDismissedAtMillis = now - days(31),
                countsByGoalId = mapOf("candidate" to 2),
            ),
        )

        assertEquals("better_candidate", result)
    }

    @Test
    fun `rolling ninety-day prompt limit is independent of dismissal`() {
        val signals = strongSignals("candidate.theme", 5)
        val scores = mapOf("declared.theme" to 10.0, "candidate.theme" to 20.0)

        assertNull(detect(signals, scores, lastPromptAtMillis = now - days(90) + 1))
        assertEquals("candidate", detect(signals, scores, lastPromptAtMillis = now - days(90)))
    }

    @Test
    fun `without a declared goal there is no weakest-goal comparison and no suggestion`() {
        assertNull(
            DivergenceDetector.suggestGoalId(
                signals = strongSignals("candidate.theme", 5),
                themeScores = mapOf("candidate.theme" to 100.0),
                declaredGoalIds = emptySet(),
                goalThemes = goalThemes,
                earliestSignalAtMillis = now - days(14),
                nowMillis = now,
                dismissalHistory = DivergenceDismissalHistory(),
                lastPromptAtMillis = null,
            ),
        )
    }

    private fun detect(
        signals: List<PersonalizationSignal>,
        themeScores: Map<String, Double>,
        earliestSignalAtMillis: Long? = now - days(14),
        dismissalHistory: DivergenceDismissalHistory = DivergenceDismissalHistory(),
        lastPromptAtMillis: Long? = null,
    ): String? = DivergenceDetector.suggestGoalId(
        signals = signals,
        themeScores = themeScores,
        declaredGoalIds = setOf("declared"),
        goalThemes = goalThemes,
        earliestSignalAtMillis = earliestSignalAtMillis,
        nowMillis = now,
        dismissalHistory = dismissalHistory,
        lastPromptAtMillis = lastPromptAtMillis,
    )

    private fun strongSignals(themeId: String, count: Int): List<PersonalizationSignal> =
        List(count) { signal(SignalType.AFFIRMATION_SHARED, themeId) }

    private fun signal(type: SignalType, themeId: String) = PersonalizationSignal(
        type = type,
        themeId = themeId,
        groupId = null,
        tone = null,
        occurredAtMillis = now,
    )

    private fun days(value: Long): Long = TimeUnit.DAYS.toMillis(value)
}
