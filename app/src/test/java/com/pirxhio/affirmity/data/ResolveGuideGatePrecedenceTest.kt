package com.pirxhio.affirmity.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers tasks 2.1/2.2 (spec R6.2, R6.3, design D3, edge case E4): [resolveGuideGate]'s
 * precedence -- the auto-show guide always wins over `healerJustGranted`, and never consumes it
 * (that state lives entirely outside this pure function, in [AffirmityAppState.healerJustGranted]).
 */
class ResolveGuideGatePrecedenceTest {

    @Test
    fun `auto-show guide takes precedence over healerJustGranted when both are true`() {
        val resolution = resolveGuideGate(autoShow = true, manualShow = false, healerJustGranted = true)

        assertEquals(GuideGateResolution.AUTO_GUIDE, resolution)
    }

    @Test
    fun `manual guide takes precedence over healerJustGranted when both are true`() {
        val resolution = resolveGuideGate(autoShow = false, manualShow = true, healerJustGranted = true)

        assertEquals(GuideGateResolution.MANUAL_GUIDE, resolution)
    }

    @Test
    fun `healerJustGranted resolves alone when neither guide gate is active`() {
        val resolution = resolveGuideGate(autoShow = false, manualShow = false, healerJustGranted = true)

        assertEquals(GuideGateResolution.HEALER_GRANTED, resolution)
    }

    @Test
    fun `none resolve when all three are false`() {
        val resolution = resolveGuideGate(autoShow = false, manualShow = false, healerJustGranted = false)

        assertEquals(GuideGateResolution.NONE, resolution)
    }

    @Test
    fun `auto-show still wins even when manual is also true -- auto is strictly earlier in gate order`() {
        val resolution = resolveGuideGate(autoShow = true, manualShow = true, healerJustGranted = true)

        assertEquals(GuideGateResolution.AUTO_GUIDE, resolution)
    }
}
