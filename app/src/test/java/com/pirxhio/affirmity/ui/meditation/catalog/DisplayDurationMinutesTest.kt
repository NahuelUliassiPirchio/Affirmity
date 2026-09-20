package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.meditation.FixedCountRepetition
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.RepetitionStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers [displayDurationMinutes], the single source of truth for list and detail duration labels. */
class DisplayDurationMinutesTest {

    private fun definitionOf(vararg phases: Phase) = MeditationDefinition(
        id = "fixture",
        root = MeditationSequence(id = "root", children = phases.toList()),
    )

    private fun singlePhase(millis: Long) = definitionOf(Phase(id = "a", duration = PhaseDuration.Fixed(millis)))

    @Test
    fun `sums Fixed phases before rounding`() {
        val definition = definitionOf(
            Phase(id = "a", duration = PhaseDuration.Fixed(60_000)),
            Phase(id = "b", duration = PhaseDuration.Fixed(29_999)),
            Phase(id = "c", duration = PhaseDuration.Fixed(1)),
        )
        // 60s + 29.999s + 0.001s = 90s -> the half-minute boundary, rounds up.
        assertEquals(2, displayDurationMinutes(definition, approxDurationMinutes = 9))
    }

    @Test
    fun `remainder of 29_999 ms rounds down`() {
        assertEquals(1, displayDurationMinutes(singlePhase(60_000 + 29_999), approxDurationMinutes = 9))
    }

    @Test
    fun `remainder of 30_000 ms rounds up`() {
        assertEquals(2, displayDurationMinutes(singlePhase(60_000 + 30_000), approxDurationMinutes = 9))
    }

    @Test
    fun `exact minute totals are unchanged`() {
        assertEquals(2, displayDurationMinutes(singlePhase(120_000), approxDurationMinutes = 9))
    }

    @Test
    fun `real-world overshoots round to the nearest minute`() {
        assertEquals(10, displayDurationMinutes(singlePhase(603_000), approxDurationMinutes = 1))
        assertEquals(10, displayDurationMinutes(singlePhase(612_000), approxDurationMinutes = 1))
        assertEquals(2, displayDurationMinutes(singlePhase(144_000), approxDurationMinutes = 1))
        assertEquals(2, displayDurationMinutes(singlePhase(110_000), approxDurationMinutes = 1))
    }

    @Test
    fun `sub-minute totals display as at least 1`() {
        assertEquals(1, displayDurationMinutes(singlePhase(5_000), approxDurationMinutes = 9))
        assertEquals(1, displayDurationMinutes(singlePhase(20_000), approxDurationMinutes = 9))
    }

    @Test
    fun `manual-only definitions fall back to the declared approx minutes`() {
        val definition = definitionOf(Phase(id = "m", duration = PhaseDuration.Manual))
        assertEquals(7, displayDurationMinutes(definition, approxDurationMinutes = 7))
    }

    @Test
    fun `manual-only definition with non-positive approx is clamped to 1`() {
        val definition = definitionOf(Phase(id = "m", duration = PhaseDuration.Manual))
        assertEquals(1, displayDurationMinutes(definition, approxDurationMinutes = 0))
    }

    @Test
    fun `Repeat multiplies its child's fixed total by the iteration count`() {
        val definition = MeditationDefinition(
            id = "repeat-fixture",
            root = Repeat(
                id = "r",
                child = Phase(id = "a", duration = PhaseDuration.Fixed(60_000)),
                strategy = FixedCountRepetition(5),
            ),
        )
        assertEquals(300_000L, fixedTotalMillis(definition))
        assertEquals(5, displayDurationMinutes(definition, approxDurationMinutes = 1))
    }

    @Test
    fun `nested Repeats multiply through and phases with the same id are not collapsed`() {
        val definition = MeditationDefinition(
            id = "nested-fixture",
            root = MeditationSequence(
                id = "root",
                children = listOf(
                    Repeat(
                        id = "outer",
                        child = MeditationSequence(
                            id = "seq",
                            children = listOf(
                                Phase(id = "same", duration = PhaseDuration.Fixed(10_000)),
                                Repeat(
                                    id = "inner",
                                    child = Phase(id = "same", duration = PhaseDuration.Fixed(5_000)),
                                    strategy = FixedCountRepetition(4),
                                ),
                            ),
                        ),
                        strategy = FixedCountRepetition(3),
                    ),
                    Phase(id = "tail", duration = PhaseDuration.Fixed(30_000)),
                ),
            ),
        )
        // 3 * (10s + 4 * 5s) + 30s = 120s
        assertEquals(120_000L, fixedTotalMillis(definition))
    }

    @Test
    fun `Repeat wrapping only Manual phases contributes zero and falls back to approx`() {
        val definition = MeditationDefinition(
            id = "manual-repeat",
            root = Repeat(
                id = "r",
                child = Phase(id = "m", duration = PhaseDuration.Manual),
                strategy = FixedCountRepetition(6),
            ),
        )
        assertEquals(0L, fixedTotalMillis(definition))
        assertEquals(7, displayDurationMinutes(definition, approxDurationMinutes = 7))
    }

    @Test
    fun `mixed Manual and Fixed phases sum only the Fixed ones`() {
        val definition = definitionOf(
            Phase(id = "m", duration = PhaseDuration.Manual),
            Phase(id = "f", duration = PhaseDuration.Fixed(180_000)),
        )
        assertEquals(180_000L, fixedTotalMillis(definition))
        assertEquals(3, displayDurationMinutes(definition, approxDurationMinutes = 9))
    }

    @Test
    fun `non-FixedCountRepetition strategy counts its child once (intentional, length is not statically knowable)`() {
        val definition = MeditationDefinition(
            id = "custom-strategy",
            root = Repeat(
                id = "r",
                child = Phase(id = "a", duration = PhaseDuration.Fixed(60_000)),
                strategy = RepetitionStrategy { _, _ -> true },
            ),
        )
        assertEquals(60_000L, fixedTotalMillis(definition))
    }

    @Test
    fun `huge totals clamp instead of wrapping negative through toInt`() {
        val definition = singlePhase(Long.MAX_VALUE / 2)
        assertEquals(Int.MAX_VALUE, displayDurationMinutes(definition, approxDurationMinutes = 5))
    }

    @Test
    fun `expectedDurationMillis is the exact fixed total, not minute-rounded`() {
        assertEquals(89_000L, expectedDurationMillis(singlePhase(89_000), approxDurationMinutes = 9))
    }

    @Test
    fun `expectedDurationMillis applies Repeat counts`() {
        val definition = MeditationDefinition(
            id = "r",
            root = Repeat("r", Phase(id = "a", duration = PhaseDuration.Fixed(10_000)), FixedCountRepetition(3)),
        )
        assertEquals(30_000L, expectedDurationMillis(definition, approxDurationMinutes = 9))
    }

    @Test
    fun `expectedDurationMillis falls back to declared approx when there is no fixed time`() {
        val definition = definitionOf(Phase(id = "m", duration = PhaseDuration.Manual))
        assertEquals(420_000L, expectedDurationMillis(definition, approxDurationMinutes = 7))
    }

    @Test
    fun `every catalog entry declares a positive approx duration`() {
        meditationCatalog().forEach { entry ->
            assertTrue("${entry.id}: approxDurationMinutes must be > 0", entry.approxDurationMinutes > 0)
        }
    }

    @Test
    fun `derived duration equals declared approx for every catalog entry`() {
        // A failure here means the catalog metadata (approxDurationMinutes) or a definition's
        // durations need updating -- not this derivation code.
        val catalog = meditationCatalog()
        assertTrue("catalog must not be empty", catalog.isNotEmpty())
        // Guard against passing vacuously through the approx fallback alone.
        val withFixedTime = catalog.count { fixedTotalMillis(it.definition(emptyMap())) > 0L }
        assertTrue("expected most entries to have fixed time, got $withFixedTime/${catalog.size}", withFixedTime * 2 > catalog.size)
        val divergent = catalog.mapNotNull { entry ->
            val definition = entry.definition(emptyMap())
            val derived = displayDurationMinutes(definition, entry.approxDurationMinutes)
            if (derived != entry.approxDurationMinutes) {
                "${entry.id}: declared=${entry.approxDurationMinutes} derived=$derived (${fixedTotalMillis(definition) / 1000}s)"
            } else {
                null
            }
        }
        assertTrue("Divergent entries: $divergent", divergent.isEmpty())
    }
}
