package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.Repeat
import com.pirxhio.affirmity.meditation.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * This test confirms `trataka`/`yoganidra`/`open_awareness`/`noting`/
 * `progressive_muscle_relaxation`/`body_scan`'s `definition` lambdas actually parse the
 * customization `config` map, following
 * [BreathingFamilyCustomizationTest]/[MindfulnessMantraCustomizationTest]/[PrayerSelfCompassionCustomizationTest]'s pattern.
 */
class BodyAwarenessCustomizationTest {

    private object NoOpCommandExecutor : MeditationCommandExecutor {
        override fun execute(command: MeditationCommand) = Unit
    }

    private fun phaseCount(entry: MeditationCatalogEntry, config: Map<String, String>): Int {
        val engine = MeditationEngine(entry.definition(config), NoOpCommandExecutor)
        engine.send(MeditationEvent.Start)
        var count = 0
        while (engine.state.value.status == SessionStatus.Running) {
            count++
            engine.send(MeditationEvent.Next)
        }
        assertEquals(SessionStatus.Completed, engine.state.value.status)
        return count
    }

    @Test
    fun `trataka custom rounds and focusSeconds drive the repeat`() {
        val entry = requireNotNull(findMeditationCatalogEntry("trataka"))
        // preparation(1) + 4 rounds x 2 + rest(1) = 10 phases, vs default 2 rounds = 6 phases
        assertEquals(10, phaseCount(entry, mapOf("rounds" to "4")))
        assertEquals(6, phaseCount(entry, emptyMap()))

        val definition = entry.definition(mapOf("rounds" to "4", "focusSeconds" to "60"))
        val sequence = definition.root as MeditationSequence
        val repeat = sequence.children[1] as Repeat
        val round = repeat.child as MeditationSequence
        val focus = round.children[0] as Phase
        assertEquals(60_000L, (focus.duration as PhaseDuration.Fixed).millis)
    }

    @Test
    fun `trataka custom focusObject reaches variables`() {
        val entry = requireNotNull(findMeditationCatalogEntry("trataka"))
        assertEquals("candle", entry.definition(emptyMap()).variables["focusObject"])
        assertEquals("dot", entry.definition(mapOf("focusObject" to "dot")).variables["focusObject"])
    }

    @Test
    fun `yoganidra custom duration scales all spans proportionally`() {
        val entry = requireNotNull(findMeditationCatalogEntry("yoganidra"))
        val definition = entry.definition(mapOf("durationMinutes" to "10"))
        val sequence = definition.root as MeditationSequence
        val totalMillis = sequence.children.sumOf { (it as Phase).duration.let { d -> (d as PhaseDuration.Fixed).millis } }
        assertEquals(600_000L, totalMillis)
    }

    @Test
    fun `open_awareness custom duration scales the open-awareness span`() {
        val entry = requireNotNull(findMeditationCatalogEntry("open_awareness"))
        val definition = entry.definition(mapOf("durationMinutes" to "5"))
        val sequence = definition.root as MeditationSequence
        val open = sequence.children[2] as Phase
        // 5min total - 120s anchor - 60s expand = 120s open awareness
        assertEquals(120_000L, (open.duration as PhaseDuration.Fixed).millis)
    }

    @Test
    fun `noting custom duration scales the noting span`() {
        val entry = requireNotNull(findMeditationCatalogEntry("noting"))
        val definition = entry.definition(mapOf("durationMinutes" to "5"))
        val sequence = definition.root as MeditationSequence
        val noting = sequence.children[1] as Phase
        // 5min total - 120s breath anchor - 60s open awareness = 120s noting
        assertEquals(120_000L, (noting.duration as PhaseDuration.Fixed).millis)
    }

    @Test
    fun `progressive_muscle_relaxation custom groups and timings drive the sequence`() {
        val entry = requireNotNull(findMeditationCatalogEntry("progressive_muscle_relaxation"))
        val definition = entry.definition(
            mapOf(
                "muscleGroups" to "feet|hands",
                "tenseSeconds" to "3",
                "relaxSeconds" to "10",
            ),
        )
        val sequence = definition.root as MeditationSequence
        // settling(1) + 2 groups x (tense+relax) + whole-body-rest(1) = 6
        assertEquals(6, sequence.children.size)
        val tenseFeet = sequence.children[1] as Phase
        assertEquals(3_000L, (tenseFeet.duration as PhaseDuration.Fixed).millis)
    }

    @Test
    fun `body_scan direction reverses region traversal order`() {
        val entry = requireNotNull(findMeditationCatalogEntry("body_scan"))
        val forward = entry.definition(mapOf("direction" to "feet_to_head")).root as MeditationSequence
        val reversed = entry.definition(mapOf("direction" to "head_to_feet")).root as MeditationSequence
        val forwardRegionIds = forward.children.filterIsInstance<Phase>()
            .map { it.id }
            .filter { it.startsWith("bs_") && it != "bs_intro" && it != "bs_close" && it != "bs_whole" }
        val reversedRegionIds = reversed.children.filterIsInstance<Phase>()
            .map { it.id }
            .filter { it.startsWith("bs_") && it != "bs_intro" && it != "bs_close" && it != "bs_whole" }
        assertEquals(forwardRegionIds.reversed(), reversedRegionIds)
    }

    @Test
    fun `body_scan default behavior unchanged for zero-arg call`() {
        val entry = requireNotNull(findMeditationCatalogEntry("body_scan"))
        assertEquals(20, phaseCount(entry, emptyMap()))
    }
}
