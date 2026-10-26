package com.pirxhio.affirmity.meditation.authoring

import com.pirxhio.affirmity.meditation.BreathRole
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BreathingBlocksTest {

    private fun roles(block: com.pirxhio.affirmity.meditation.Repeat): Map<String, BreathRole?> =
        (block.child as MeditationSequence).children.map { it as Phase }.associate { it.id to it.breathRole }

    @Test
    fun `all four sub-phases carry their breath role`() {
        val block = breathingBlock(
            breaths = 3,
            inhaleMillis = 4_000,
            exhaleMillis = 4_000,
            holdAfterInhaleMillis = 4_000,
            holdAfterExhaleMillis = 4_000,
        )

        assertEquals(
            mapOf(
                "inhale" to BreathRole.INHALE,
                "hold_in" to BreathRole.HOLD_FULL,
                "exhale" to BreathRole.EXHALE,
                "hold_out" to BreathRole.HOLD_EMPTY,
            ),
            roles(block),
        )
    }

    @Test
    fun `the block is not skip-marked unless asked`() {
        assertFalse(breathingBlock(breaths = 1, inhaleMillis = 1, exhaleMillis = 1).skipExitsBlock)
        assertEquals(
            true,
            breathingBlock(breaths = 1, inhaleMillis = 1, exhaleMillis = 1, skippableAsBlock = true).skipExitsBlock,
        )
    }
}
