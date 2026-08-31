package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * This test confirms each mindfulness/silence/movement/mantra entry's `definition` lambda
 * actually parses the customization `config` map rather than silently ignoring it, following
 * [BreathingFamilyCustomizationTest]'s pattern.
 */
class MindfulnessMantraCustomizationTest {

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
    fun `anapanasati custom duration drives awareness phase duration`() {
        val entry = requireNotNull(findMeditationCatalogEntry("anapanasati"))
        val definition = entry.definition(mapOf("durationMinutes" to "5"))
        val sequence = definition.root as com.pirxhio.affirmity.meditation.MeditationSequence
        val awareness = sequence.children[1] as com.pirxhio.affirmity.meditation.Phase
        val duration = awareness.duration as com.pirxhio.affirmity.meditation.PhaseDuration.Fixed
        // 5min total - 60s arrival - 60s closing = 180s awareness
        assertEquals(180_000L, duration.millis)
    }

    @Test
    fun `vipassana custom duration drives awareness phase durations`() {
        val entry = requireNotNull(findMeditationCatalogEntry("vipassana"))
        val definition = entry.definition(mapOf("durationMinutes" to "20"))
        val sequence = definition.root as com.pirxhio.affirmity.meditation.MeditationSequence
        val bodyAwareness = sequence.children[1] as com.pirxhio.affirmity.meditation.Phase
        val duration = bodyAwareness.duration as com.pirxhio.affirmity.meditation.PhaseDuration.Fixed
        // 20min total - 180s anchor - 60s closing = 960s, split 50/50 = 480s each
        assertEquals(480_000L, duration.millis)
    }

    @Test
    fun `metta custom targets drive phase count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("metta"))
        assertEquals(5, phaseCount(entry, emptyMap()))
        assertEquals(2, phaseCount(entry, mapOf("targets" to "self|all_beings")))
    }

    @Test
    fun `zazen custom opening instructions toggle drives phase count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("zazen"))
        assertEquals(8, phaseCount(entry, emptyMap()))
        assertEquals(7, phaseCount(entry, mapOf("openingInstructions" to "false")))
    }

    @Test
    fun `walking_meditation custom duration drives walking phase duration`() {
        val entry = requireNotNull(findMeditationCatalogEntry("walking_meditation"))
        val definition = entry.definition(mapOf("durationMinutes" to "5"))
        val sequence = definition.root as com.pirxhio.affirmity.meditation.MeditationSequence
        val walking = sequence.children[2] as com.pirxhio.affirmity.meditation.Phase
        val duration = walking.duration as com.pirxhio.affirmity.meditation.PhaseDuration.Fixed
        // 5min total - 60s arrival - 60s standing - 60s closing = 120s walking
        assertEquals(120_000L, duration.millis)
    }

    @Test
    fun `mantra_meditation custom duration drives mantra phase duration`() {
        val entry = requireNotNull(findMeditationCatalogEntry("mantra_meditation"))
        val definition = entry.definition(mapOf("durationMinutes" to "5"))
        val sequence = definition.root as com.pirxhio.affirmity.meditation.MeditationSequence
        val mantra = sequence.children[1] as com.pirxhio.affirmity.meditation.Phase
        val duration = mantra.duration as com.pirxhio.affirmity.meditation.PhaseDuration.Fixed
        // 5min total - 60s preparation - 60s silence = 180s mantra
        assertEquals(180_000L, duration.millis)
    }

    @Test
    fun `om_meditation custom rounds drives phase count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("om_meditation"))
        assertEquals(6, phaseCount(entry, mapOf("rounds" to "3")))
    }

    @Test
    fun `so_hum custom duration and pace drive breath count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("so_hum"))
        // 5min = 300s, natural pace = 9s/breath -> 33 breaths -> 66 phases
        assertEquals(66, phaseCount(entry, mapOf("durationMinutes" to "5")))
    }
}
