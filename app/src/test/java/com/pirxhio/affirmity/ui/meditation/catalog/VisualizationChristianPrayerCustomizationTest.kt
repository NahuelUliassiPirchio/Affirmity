package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import com.pirxhio.affirmity.meditation.gratitudemeditation.GratitudeMeditationText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * This test confirms `visualization`/`gratitude_meditation`/`centering_prayer`/
 * `jesus_prayer`/`lectio_divina`'s `definition` lambdas actually parse the customization `config`
 * map, following [BreathingFamilyCustomizationTest]/[MindfulnessMantraCustomizationTest]/
 * [PrayerSelfCompassionCustomizationTest]/[BodyAwarenessCustomizationTest]'s pattern. Also
 * exercises `lectio_divina`'s `minutesPerStage`
 * [com.pirxhio.affirmity.meditation.customization.CustomizationField.Group] -- the first real
 * usage of nested-group config keys in this catalog.
 */
class VisualizationChristianPrayerCustomizationTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    /** All 5 entries in this batch build a flat [MeditationSequence] of [Phase]s (no [Repeat]),
     * so summing children's [PhaseDuration.Fixed] millis directly is equivalent to -- and simpler
     * than -- driving the engine to completion, matching BodyAwarenessCustomizationTest's `yoganidra` test approach. */
    private fun totalMillis(entry: MeditationCatalogEntry, config: Map<String, String>): Long =
        (entry.definition(config).root as MeditationSequence).children
            .sumOf { (it as Phase).duration.let { d -> (d as PhaseDuration.Fixed).millis } }

    @Test
    fun `visualization custom duration scales the visualization span only`() {
        val entry = requireNotNull(findMeditationCatalogEntry("visualization"))
        assertEquals(720_000L, totalMillis(entry, emptyMap()))
        assertEquals(600_000L, totalMillis(entry, mapOf("durationMinutes" to "10")))
    }

    @Test
    fun `visualization custom scenario and backgroundSound reach variables`() {
        val entry = requireNotNull(findMeditationCatalogEntry("visualization"))
        val default = entry.definition(emptyMap())
        assertEquals("nature", default.variables["scenario"])
        assertEquals("none", default.variables["backgroundSound"])

        val custom = entry.definition(mapOf("scenario" to "goal", "backgroundSound" to "rain"))
        assertEquals("goal", custom.variables["scenario"])
        assertEquals("rain", custom.variables["backgroundSound"])
    }

    @Test
    fun `gratitude promptCount of 1 runs one fewer phase than default`() {
        val entry = requireNotNull(findMeditationCatalogEntry("gratitude_meditation"))
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(entry.definition(mapOf("promptCount" to "1")), executor)
        engine.send(MeditationEvent.Start)
        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }
        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(2, phaseCount)
        assertTrue(executor.commands.none { it is ShowText && it.textId == GratitudeMeditationText.PRESENT })
    }

    @Test
    fun `gratitude custom journalAtEnd reaches variables`() {
        val entry = requireNotNull(findMeditationCatalogEntry("gratitude_meditation"))
        assertEquals(false, entry.definition(emptyMap()).variables["journalAtEnd"])
        assertEquals(true, entry.definition(mapOf("journalAtEnd" to "true")).variables["journalAtEnd"])
    }

    @Test
    fun `centering prayer openingGuidance false omits the sacred word phase`() {
        val entry = requireNotNull(findMeditationCatalogEntry("centering_prayer"))
        val definitionOn = entry.definition(emptyMap())
        val definitionOff = entry.definition(mapOf("openingGuidance" to "false"))
        val onChildren = (definitionOn.root as MeditationSequence).children
        val offChildren = (definitionOff.root as MeditationSequence).children
        assertEquals(3, onChildren.size)
        assertEquals(2, offChildren.size)
        assertEquals(1_200_000L, totalMillis(entry, emptyMap()))
        assertEquals(1_200_000L, totalMillis(entry, mapOf("openingGuidance" to "false")))
    }

    @Test
    fun `centering prayer custom sacredWord reaches variables`() {
        val entry = requireNotNull(findMeditationCatalogEntry("centering_prayer"))
        assertEquals(null, entry.definition(emptyMap()).variables["sacredWord"])
        assertEquals("peace", entry.definition(mapOf("sacredWord" to "peace")).variables["sacredWord"])
    }

    @Test
    fun `jesus prayer custom duration changes breath count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("jesus_prayer"))
        assertEquals(67, entry.definition(emptyMap()).variables["breaths"])
        assertEquals(200, entry.definition(mapOf("durationMinutes" to "30")).variables["breaths"])
    }

    @Test
    fun `jesus prayer custom syncWithBreath and repetitionMode reach variables`() {
        val entry = requireNotNull(findMeditationCatalogEntry("jesus_prayer"))
        val default = entry.definition(emptyMap())
        assertEquals(true, default.variables["syncWithBreath"])
        assertEquals("mental", default.variables["repetitionMode"])

        val custom = entry.definition(mapOf("syncWithBreath" to "false", "repetitionMode" to "spoken"))
        assertEquals(false, custom.variables["syncWithBreath"])
        assertEquals("spoken", custom.variables["repetitionMode"])
    }

    @Test
    fun `lectio divina minutesPerStage group drives each stage duration independently`() {
        val entry = requireNotNull(findMeditationCatalogEntry("lectio_divina"))
        assertEquals(900_000L, totalMillis(entry, emptyMap()))

        val config = mapOf(
            "minutesPerStage.lectio" to "5",
            "minutesPerStage.meditatio" to "6",
            "minutesPerStage.oratio" to "2",
            "minutesPerStage.contemplatio" to "7",
        )
        val definition = entry.definition(config)
        val children = (definition.root as MeditationSequence).children.map { it as Phase }
        assertEquals(300_000L, (children[0].duration as PhaseDuration.Fixed).millis)
        assertEquals(360_000L, (children[1].duration as PhaseDuration.Fixed).millis)
        assertEquals(120_000L, (children[2].duration as PhaseDuration.Fixed).millis)
        assertEquals(420_000L, (children[3].duration as PhaseDuration.Fixed).millis)
        assertEquals(1_200_000L, totalMillis(entry, config))
    }

    @Test
    fun `lectio divina custom passage and guidedPrompts reach variables`() {
        val entry = requireNotNull(findMeditationCatalogEntry("lectio_divina"))
        val default = entry.definition(emptyMap())
        assertEquals(null, default.variables["passage"])
        assertEquals(true, default.variables["guidedPrompts"])

        val custom = entry.definition(mapOf("passage" to "Psalm 23", "guidedPrompts" to "false"))
        assertEquals("Psalm 23", custom.variables["passage"])
        assertEquals(false, custom.variables["guidedPrompts"])
    }
}
