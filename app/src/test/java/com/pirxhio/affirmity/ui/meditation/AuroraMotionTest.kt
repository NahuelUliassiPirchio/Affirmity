package com.pirxhio.affirmity.ui.meditation

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuroraMotionTest {

    private val width = 1080f
    private val height = 2400f

    @Test
    fun `centers stay within the drift amplitude of their anchor`() {
        for (index in 0 until AURORA_FIELD_COUNT) {
            val anchor = auroraFieldCenter(index, 0.0, width, height)
            var t = 0.0
            while (t < 300.0) {
                val c = auroraFieldCenter(index, t, width, height)
                // Amplitude is 0.12 of the width; the anchor is the t=0 point, so the farthest the
                // path can be from it is the full ellipse diameter, 2 * 0.12 * width (~0.24w).
                assertTrue(abs(c.x - anchor.x) <= 0.24f * width + 0.5f)
                assertTrue(abs(c.y - anchor.y) <= 0.24f * width + 0.5f)
                t += 1.7
            }
        }
    }

    @Test
    fun `motion is continuous - a small time step moves a small distance`() {
        for (index in 0 until AURORA_FIELD_COUNT) {
            var t = 0.0
            while (t < 200.0) {
                val a = auroraFieldCenter(index, t, width, height)
                val b = auroraFieldCenter(index, t + 0.016, width, height)
                // Peak speed is well under 20 px/s, so one frame is a fraction of a pixel.
                assertTrue(hypot(b.x - a.x, b.y - a.y) < 0.5f)
                t += 3.1
            }
        }
    }

    @Test
    fun `each field repeats after its own period`() {
        AURORA_PERIODS_SECONDS.forEachIndexed { index, period ->
            val start = auroraFieldCenter(index, 12.5, width, height)
            val later = auroraFieldCenter(index, 12.5 + period, width, height)
            assertEquals(start.x, later.x, 0.05f)
            assertEquals(start.y, later.y, 0.05f)
        }
    }

    @Test
    fun `periods are pairwise coprime so the fields never realign`() {
        fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
        val p = AURORA_PERIODS_SECONDS.map { it.toLong() }
        for (i in p.indices) for (j in i + 1 until p.size) assertEquals(1L, gcd(p[i], p[j]))
    }

    @Test
    fun `fields start apart from each other`() {
        val centers = (0 until AURORA_FIELD_COUNT).map { auroraFieldCenter(it, 0.0, width, height) }
        for (i in centers.indices) for (j in i + 1 until centers.size) {
            assertTrue(hypot(centers[i].x - centers[j].x, centers[i].y - centers[j].y) > 0.2f * width)
        }
    }

    @Test
    fun `radius sits between 0_6 and 0_9 of the larger screen dimension`() {
        for (index in 0 until AURORA_FIELD_COUNT) {
            val r = auroraFieldRadius(index, width, height)
            assertTrue(r >= 0.6f * height && r <= 0.9f * height)
        }
        // Landscape: the larger dimension is the width.
        val r = auroraFieldRadius(0, 2400f, 1080f)
        assertTrue(r >= 0.6f * 2400f && r <= 0.9f * 2400f)
    }

    @Test
    fun `accumulator advances only while running`() {
        assertEquals(1.5, advanceAuroraTime(1.0, 0.5, running = true), 1e-9)
        assertEquals(1.0, advanceAuroraTime(1.0, 0.5, running = false), 1e-9)
    }
}
