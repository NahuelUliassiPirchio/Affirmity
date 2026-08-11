package com.pirxhio.affirmity.meditation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedCountRepetitionTest {

    private val context = MeditationRuntimeState()

    @Test
    fun `continues after every iteration below the configured count`() {
        val strategy = FixedCountRepetition(times = 3)

        assertTrue(strategy.shouldContinue(completedIterationIndex = 0, context))
        assertTrue(strategy.shouldContinue(completedIterationIndex = 1, context))
    }

    @Test
    fun `stops once the configured count is reached`() {
        val strategy = FixedCountRepetition(times = 3)

        assertFalse(strategy.shouldContinue(completedIterationIndex = 2, context))
    }

    @Test
    fun `a single-iteration repetition stops immediately`() {
        val strategy = FixedCountRepetition(times = 1)

        assertFalse(strategy.shouldContinue(completedIterationIndex = 0, context))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a non-positive count`() {
        FixedCountRepetition(times = 0)
    }
}
