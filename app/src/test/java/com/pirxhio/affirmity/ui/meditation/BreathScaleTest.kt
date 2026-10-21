package com.pirxhio.affirmity.ui.meditation

import com.pirxhio.affirmity.meditation.BreathRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathScaleTest {

    private val delta = 1e-4f

    @Test
    fun `an inhale grows from the empty size to full`() {
        assertEquals(BREATH_SCALE_MIN, breathScale(BreathRole.INHALE, 0f), delta)
        assertEquals(BREATH_SCALE_MAX, breathScale(BreathRole.INHALE, 1f), delta)
    }

    @Test
    fun `an exhale shrinks from full to the empty size`() {
        assertEquals(BREATH_SCALE_MAX, breathScale(BreathRole.EXHALE, 0f), delta)
        assertEquals(BREATH_SCALE_MIN, breathScale(BreathRole.EXHALE, 1f), delta)
    }

    @Test
    fun `the midpoint of an inhale or exhale is about 0_71`() {
        assertEquals(0.71f, breathScale(BreathRole.INHALE, 0.5f), 0.01f)
        assertEquals(0.71f, breathScale(BreathRole.EXHALE, 0.5f), 0.01f)
    }

    @Test
    fun `holds stay constant at any fraction`() {
        listOf(0f, 0.3f, 1f).forEach {
            assertEquals(BREATH_SCALE_MAX, breathScale(BreathRole.HOLD_FULL, it), delta)
            assertEquals(BREATH_SCALE_MIN, breathScale(BreathRole.HOLD_EMPTY, it), delta)
        }
    }

    @Test
    fun `fractions outside 0 to 1 are clamped`() {
        assertEquals(breathScale(BreathRole.INHALE, 0f), breathScale(BreathRole.INHALE, -2f), delta)
        assertEquals(breathScale(BreathRole.INHALE, 1f), breathScale(BreathRole.INHALE, 3f), delta)
    }

    @Test
    fun `an inhale never shrinks as it progresses`() {
        val samples = (0..20).map { breathScale(BreathRole.INHALE, it / 20f) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b >= a })
    }
}
