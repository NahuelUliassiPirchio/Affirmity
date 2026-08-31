package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.customization.resolvedValues
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * This test confirms each breathing-family entry's `definition` lambda actually parses
 * the customization `config` map (as `MeditationCustomizationScreen` -> `resolvedValues` would
 * hand it) rather than silently ignoring it, by driving the built [MeditationEngine] to
 * completion and counting phases.
 */
class BreathingFamilyCustomizationTest {

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
    fun `box_breathing custom rounds drives phase count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("box_breathing"))
        assertEquals(8, phaseCount(entry, mapOf("rounds" to "2")))
    }

    @Test
    fun `breathing_4_7_8 custom rounds drives phase count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("breathing_4_7_8"))
        assertEquals(6, phaseCount(entry, mapOf("rounds" to "2")))
    }

    @Test
    fun `extended_exhale custom rounds drives phase count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("extended_exhale"))
        assertEquals(6, phaseCount(entry, mapOf("rounds" to "3")))
    }

    @Test
    fun `coherent_breathing custom duration and rate drive phase count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("coherent_breathing"))
        // 2 min * 5 breaths/min = 10 breaths -> 20 phases (inhale+exhale each)
        assertEquals(20, phaseCount(entry, mapOf("durationMinutes" to "2", "breathsPerMinute" to "5")))
    }

    @Test
    fun `nadi_shodhana custom rounds and retention drive phase count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("nadi_shodhana"))
        assertEquals(8, phaseCount(entry, mapOf("rounds" to "2")))
        assertEquals(6, phaseCount(entry, mapOf("rounds" to "1", "retention" to "true")))
    }

    @Test
    fun `nadi_shodhana out-of-range persisted rounds is clamped instead of crashing`() {
        val entry = requireNotNull(findMeditationCatalogEntry("nadi_shodhana"))

        val resolvedBelowMin = resolvedValues(entry.customizationFields, mapOf("rounds" to "-5"))
        val resolvedAboveMax = resolvedValues(entry.customizationFields, mapOf("rounds" to "9999"))

        val roundsField = entry.customizationFields
            .filterIsInstance<com.pirxhio.affirmity.meditation.customization.CustomizationField.IntSlider>()
            .first { it.key == "rounds" }
        assertEquals(roundsField.min.toString(), resolvedBelowMin.getValue("rounds"))
        assertEquals(roundsField.max.toString(), resolvedAboveMax.getValue("rounds"))

        // Must not throw the `require(config.rounds > 0)` IllegalArgumentException.
        entry.definition(resolvedBelowMin)
        entry.definition(resolvedAboveMax)
    }

    @Test
    fun `box_breathing out-of-range persisted rounds is clamped instead of crashing`() {
        val entry = requireNotNull(findMeditationCatalogEntry("box_breathing"))

        val resolvedBelowMin = resolvedValues(entry.customizationFields, mapOf("rounds" to "-5"))
        val resolvedAboveMax = resolvedValues(entry.customizationFields, mapOf("rounds" to "9999"))

        val roundsField = entry.customizationFields
            .filterIsInstance<com.pirxhio.affirmity.meditation.customization.CustomizationField.IntSlider>()
            .first { it.key == "rounds" }
        assertEquals(roundsField.min.toString(), resolvedBelowMin.getValue("rounds"))
        assertEquals(roundsField.max.toString(), resolvedAboveMax.getValue("rounds"))

        entry.definition(resolvedBelowMin)
        entry.definition(resolvedAboveMax)
    }

    @Test
    fun `bhramari custom rounds drives phase count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("bhramari"))
        assertEquals(6, phaseCount(entry, mapOf("rounds" to "3")))
    }

    @Test
    fun `kapalabhati custom rounds drives phase count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("kapalabhati"))
        // 2 rounds * 3 phases per round (preparation, active, rest)
        assertEquals(6, phaseCount(entry, mapOf("rounds" to "2")))
    }

    @Test
    fun `breath_of_fire custom rounds drives phase count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("breath_of_fire"))
        assertEquals(6, phaseCount(entry, mapOf("rounds" to "2")))
    }
}
