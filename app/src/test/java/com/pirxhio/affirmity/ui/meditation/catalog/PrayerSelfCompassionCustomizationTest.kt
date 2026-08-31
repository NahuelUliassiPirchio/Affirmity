package com.pirxhio.affirmity.ui.meditation.catalog

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.MeditationSequence
import com.pirxhio.affirmity.meditation.Phase
import com.pirxhio.affirmity.meditation.PhaseDuration
import com.pirxhio.affirmity.meditation.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * This test confirms `dhikr`/`muraqabah`/`hitbodedut`/`self_compassion_break`'s
 * `definition` lambdas actually parse the customization `config` map, following
 * [BreathingFamilyCustomizationTest]/[MindfulnessMantraCustomizationTest]'s pattern.
 */
class PrayerSelfCompassionCustomizationTest {

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
    fun `dhikr custom repetitions drives repeat count`() {
        val entry = requireNotNull(findMeditationCatalogEntry("dhikr"))
        // intention(1) + repetitions(11) + silence(1) = 13 phases
        assertEquals(13, phaseCount(entry, mapOf("repetitions" to "11")))
        // default: intention(1) + repetitions(33) + silence(1) = 35 phases
        assertEquals(35, phaseCount(entry, emptyMap()))
    }

    @Test
    fun `muraqabah custom duration drives contemplation phase duration`() {
        val entry = requireNotNull(findMeditationCatalogEntry("muraqabah"))
        val definition = entry.definition(mapOf("durationMinutes" to "15"))
        val sequence = definition.root as MeditationSequence
        val contemplation = sequence.children[2] as Phase
        val duration = contemplation.duration as PhaseDuration.Fixed
        // 15min total - 60s intention - 120s breath-settling = 720s contemplation
        assertEquals(720_000L, duration.millis)
    }

    @Test
    fun `hitbodedut default runs 3 default prompt topics - 6 phases`() {
        val entry = requireNotNull(findMeditationCatalogEntry("hitbodedut"))
        assertEquals(6, phaseCount(entry, emptyMap()))
    }

    @Test
    fun `hitbodedut guidedPrompts disabled collapses reflection to one silent phase - 4 phases`() {
        val entry = requireNotNull(findMeditationCatalogEntry("hitbodedut"))
        assertEquals(4, phaseCount(entry, mapOf("guidedPrompts" to "false")))
    }

    @Test
    fun `hitbodedut all six prompt topics selected - 9 phases`() {
        val entry = requireNotNull(findMeditationCatalogEntry("hitbodedut"))
        assertEquals(
            9,
            phaseCount(
                entry,
                mapOf("promptTopics" to "gratitude|concerns|requests|relationships|purpose|forgiveness"),
            ),
        )
    }

    @Test
    fun `self_compassion_break custom duration scales the three reflection phases`() {
        val entry = requireNotNull(findMeditationCatalogEntry("self_compassion_break"))
        val definition = entry.definition(mapOf("durationMinutes" to "5"))
        val sequence = definition.root as MeditationSequence
        // 5min total - 60s integration = 240s split 1:1:2 -> 60/60/120
        val recognize = sequence.children[0] as Phase
        val kindness = sequence.children[2] as Phase
        assertEquals(60_000L, (recognize.duration as PhaseDuration.Fixed).millis)
        assertEquals(120_000L, (kindness.duration as PhaseDuration.Fixed).millis)
    }
}
