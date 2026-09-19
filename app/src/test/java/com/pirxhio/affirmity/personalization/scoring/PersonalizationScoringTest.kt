package com.pirxhio.affirmity.personalization.scoring

import com.pirxhio.affirmity.personalization.signal.PersonalizationSignal
import com.pirxhio.affirmity.personalization.signal.SignalType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED-first (design D4 / spec personalization-signals). [PersonalizationScoring] is a pure
 * object -- every case here is a fixed `signals` + fixed `nowMillis` in, deterministic
 * [PersonalizationProfile] out. No Room, no Context, no clock reads.
 */
class PersonalizationScoringTest {

    private val now = 1_700_000_000_000L
    private val dayMillis = 24L * 60 * 60 * 1000

    private fun signal(
        type: SignalType = SignalType.AFFIRMATION_SAVED,
        themeId: String? = "self_worth.feeling_enough",
        groupId: String? = "self_worth",
        tone: String? = "powerful",
        ageDays: Long = 0,
    ) = PersonalizationSignal(
        type = type,
        themeId = themeId,
        groupId = groupId,
        tone = tone,
        occurredAtMillis = now - ageDays * dayMillis,
    )

    @Test
    fun `signal aged 7 days contributes full weight`() {
        val profile = PersonalizationScoring.profile(
            signals = listOf(signal(ageDays = 7)),
            declaredGoalIds = emptySet(),
            goalThemes = emptyMap(),
            nowMillis = now,
        )
        // AFFIRMATION_SAVED default weight is 5; 100% band -> 5.0
        assertEquals(5.0, profile.themeScores.getValue("self_worth.feeling_enough"), 0.0001)
    }

    @Test
    fun `signal aged 8 days drops to 70 percent band`() {
        val profile = PersonalizationScoring.profile(
            signals = listOf(signal(ageDays = 8)),
            declaredGoalIds = emptySet(),
            goalThemes = emptyMap(),
            nowMillis = now,
        )
        assertEquals(3.5, profile.themeScores.getValue("self_worth.feeling_enough"), 0.0001)
    }

    @Test
    fun `signal aged 30 days stays in 70 percent band`() {
        val profile = PersonalizationScoring.profile(
            signals = listOf(signal(ageDays = 30)),
            declaredGoalIds = emptySet(),
            goalThemes = emptyMap(),
            nowMillis = now,
        )
        assertEquals(3.5, profile.themeScores.getValue("self_worth.feeling_enough"), 0.0001)
    }

    @Test
    fun `signal aged 31 days drops to 40 percent band`() {
        val profile = PersonalizationScoring.profile(
            signals = listOf(signal(ageDays = 31)),
            declaredGoalIds = emptySet(),
            goalThemes = emptyMap(),
            nowMillis = now,
        )
        assertEquals(2.0, profile.themeScores.getValue("self_worth.feeling_enough"), 0.0001)
    }

    @Test
    fun `signal aged 90 days stays in 40 percent band`() {
        val profile = PersonalizationScoring.profile(
            signals = listOf(signal(ageDays = 90)),
            declaredGoalIds = emptySet(),
            goalThemes = emptyMap(),
            nowMillis = now,
        )
        assertEquals(2.0, profile.themeScores.getValue("self_worth.feeling_enough"), 0.0001)
    }

    @Test
    fun `signal aged 91 days drops to 20 percent band`() {
        val profile = PersonalizationScoring.profile(
            signals = listOf(signal(ageDays = 91)),
            declaredGoalIds = emptySet(),
            goalThemes = emptyMap(),
            nowMillis = now,
        )
        assertEquals(1.0, profile.themeScores.getValue("self_worth.feeling_enough"), 0.0001)
    }

    @Test
    fun `cold start ranks from declared goals when fewer than 10 significant signals`() {
        // 3 significant (weight 5) signals for an undeclared theme -- below the 10 threshold.
        val signals = List(3) { signal(themeId = "undeclared.theme") }
        val profile = PersonalizationScoring.profile(
            signals = signals,
            declaredGoalIds = setOf("goal_calm"),
            goalThemes = mapOf("goal_calm" to setOf("calm.theme")),
            nowMillis = now,
        )
        assertTrue(profile.isColdStart)
        assertTrue(profile.themeScores.getValue("calm.theme") > profile.themeScores.getValue("undeclared.theme"))
    }

    @Test
    fun `crossing the significant-signal threshold exits cold start`() {
        val signals = List(10) { signal() }
        val profile = PersonalizationScoring.profile(
            signals = signals,
            declaredGoalIds = emptySet(),
            goalThemes = emptyMap(),
            nowMillis = now,
        )
        assertEquals(false, profile.isColdStart)
        assertEquals(10, profile.significantSignalCount)
    }

    @Test
    fun `empty signals and no declared goals returns a deterministic empty profile`() {
        val profile = PersonalizationScoring.profile(
            signals = emptyList(),
            declaredGoalIds = emptySet(),
            goalThemes = emptyMap(),
            nowMillis = now,
        )
        assertTrue(profile.themeScores.isEmpty())
        assertTrue(profile.universeScores.isEmpty())
        assertNull(profile.dominantTone)
        assertEquals(0, profile.significantSignalCount)
        assertTrue(profile.isColdStart)
    }

    @Test
    fun `identical inputs produce identical scores across invocations`() {
        val signals = listOf(signal(ageDays = 2), signal(type = SignalType.AFFIRMATION_VIEWED, ageDays = 40))
        val first = PersonalizationScoring.profile(signals, setOf("g"), mapOf("g" to setOf("t")), now)
        val second = PersonalizationScoring.profile(signals, setOf("g"), mapOf("g" to setOf("t")), now)
        assertEquals(first, second)
    }
}
