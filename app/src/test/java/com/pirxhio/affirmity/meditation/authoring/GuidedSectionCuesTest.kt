package com.pirxhio.affirmity.meditation.authoring

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationDefinition
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.PlayCue
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.StartTimer
import com.pirxhio.affirmity.meditation.anapanasati.anapanasatiMeditationDefinition
import com.pirxhio.affirmity.meditation.audio.MeditationCue
import com.pirxhio.affirmity.meditation.breathingaffirmations.BreathingAffirmationsConfig
import com.pirxhio.affirmity.meditation.breathingaffirmations.breathingAffirmationsMeditationDefinition
import com.pirxhio.affirmity.meditation.dhikr.dhikrMeditationDefinition
import com.pirxhio.affirmity.meditation.gratitudemeditation.GratitudeConfig
import com.pirxhio.affirmity.meditation.gratitudemeditation.gratitudeMeditationDefinition
import com.pirxhio.affirmity.meditation.lectiodivina.lectioDivinaMeditationDefinition
import com.pirxhio.affirmity.meditation.mantra.mantraMeditationDefinition
import com.pirxhio.affirmity.meditation.muraqabah.muraqabahMeditationDefinition
import com.pirxhio.affirmity.meditation.noting.notingMeditationDefinition
import com.pirxhio.affirmity.meditation.selfcompassion.SelfCompassionLength
import com.pirxhio.affirmity.meditation.selfcompassion.selfCompassionMeditationDefinition
import com.pirxhio.affirmity.meditation.selfcompassionbreak.selfCompassionBreakMeditationDefinition
import com.pirxhio.affirmity.meditation.trataka.TratakaConfig
import com.pirxhio.affirmity.meditation.trataka.tratakaMeditationDefinition
import com.pirxhio.affirmity.meditation.vipassana.vipassanaMeditationDefinition
import com.pirxhio.affirmity.meditation.visualization.visualizationMeditationDefinition
import com.pirxhio.affirmity.meditation.walking.walkingMeditationDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs each guided meditation to completion and asserts the ordered phase entries that chime. */
class GuidedSectionCuesTest {

    private class Recorder : MeditationCommandExecutor {
        val commands = mutableListOf<MeditationCommand>()
        override fun execute(command: MeditationCommand) {
            commands += command
        }
    }

    private class Run(val firstPhaseId: String, val chimedPhaseIds: List<String>, val maxChimesPerEntry: Int)

    private fun run(definition: MeditationDefinition): Run {
        val recorder = Recorder()
        val engine = MeditationEngine(definition, recorder)
        val chimes = mutableListOf<String>()
        var seen = 0
        var maxPerEntry = 0
        var first: String? = null

        fun sample() {
            val total = recorder.commands.count { it is PlayCue && it.cue == MeditationCue.SectionTransition }
            val delta = total - seen
            seen = total
            maxPerEntry = maxOf(maxPerEntry, delta)
            val phaseId = engine.state.value.currentPhaseId
            if (first == null) first = phaseId
            repeat(delta) { chimes += phaseId ?: "<none>" }
        }

        engine.send(MeditationEvent.Start)
        sample()
        while (engine.state.value.status == SessionStatus.Running) {
            val gen = (recorder.commands.last { it is StartTimer } as StartTimer).generation
            engine.send(MeditationEvent.TimerCompleted(gen))
            sample()
        }
        return Run(first!!, chimes, maxPerEntry)
    }

    private fun assertChimes(expected: List<String>, definition: MeditationDefinition) {
        val result = run(definition)
        assertEquals(expected, result.chimedPhaseIds)
        assertTrue("first phase '${result.firstPhaseId}' must not chime", result.firstPhaseId !in result.chimedPhaseIds.take(1))
        assertTrue("at most one chime per phase entry", result.maxChimesPerEntry <= 1)
    }

    @Test
    fun `anapanasati`() = assertChimes(listOf("breath_awareness", "closing"), anapanasatiMeditationDefinition())

    @Test
    fun `gratitude follows promptCount 1`() =
        assertChimes(listOf("person"), gratitudeMeditationDefinition(GratitudeConfig(promptCount = 1)))

    @Test
    fun `gratitude follows promptCount 3`() =
        assertChimes(listOf("person", "experience", "present"), gratitudeMeditationDefinition(GratitudeConfig(promptCount = 3)))

    @Test
    fun `lectio divina`() =
        assertChimes(listOf("meditatio", "oratio", "contemplatio"), lectioDivinaMeditationDefinition())

    @Test
    fun `self compassion break`() =
        assertChimes(listOf("shared_humanity", "kindness", "integration"), selfCompassionBreakMeditationDefinition())

    @Test
    fun `visualization`() =
        assertChimes(listOf("visualization", "integration", "return"), visualizationMeditationDefinition())

    @Test
    fun `self compassion short chimes the breathing block once`() =
        assertChimes(
            listOf("sc_inhale", "sc_phrase_1", "sc_phrase_2", "sc_close"),
            selfCompassionMeditationDefinition(SelfCompassionLength.SHORT),
        )

    @Test
    fun `self compassion long chimes the breathing block once`() =
        assertChimes(
            listOf("sc_inhale", "sc_phrase_1", "sc_phrase_2", "sc_phrase_3", "sc_close"),
            selfCompassionMeditationDefinition(SelfCompassionLength.LONG),
        )

    @Test
    fun `vipassana`() =
        assertChimes(listOf("body_awareness", "open_observation", "closing"), vipassanaMeditationDefinition())

    @Test
    fun `walking`() =
        assertChimes(listOf("standing_awareness", "walking", "closing"), walkingMeditationDefinition())

    @Test
    fun `muraqabah`() =
        assertChimes(listOf("breath_settling", "contemplation"), muraqabahMeditationDefinition())

    @Test
    fun `noting`() = assertChimes(listOf("noting", "open_awareness"), notingMeditationDefinition())

    @Test
    fun `breathing affirmations without user affirmations`() =
        assertChimes(listOf("meditation", "affirmations", "silence"), breathingAffirmationsMeditationDefinition())

    @Test
    fun `breathing affirmations chimes only the first affirmation of the group`() =
        assertChimes(
            listOf("meditation", "affirmation_0", "silence"),
            breathingAffirmationsMeditationDefinition(
                BreathingAffirmationsConfig(affirmationTexts = listOf("a", "b", "c")),
            ),
        )

    @Test
    fun `mantra`() = assertChimes(listOf("mantra", "silence"), mantraMeditationDefinition())

    @Test
    fun `dhikr chimes the counted phase once, not per repetition`() =
        assertChimes(listOf("repetition", "silence"), dhikrMeditationDefinition())

    @Test
    fun `trataka default two rounds`() =
        assertChimes(
            listOf("external_focus", "eyes_closed", "external_focus", "eyes_closed", "rest"),
            tratakaMeditationDefinition(),
        )

    @Test
    fun `trataka one round`() =
        assertChimes(listOf("external_focus", "eyes_closed", "rest"), tratakaMeditationDefinition(TratakaConfig(rounds = 1)))

    @Test
    fun `trataka three rounds`() =
        assertChimes(
            List(3) { listOf("external_focus", "eyes_closed") }.flatten() + "rest",
            tratakaMeditationDefinition(TratakaConfig(rounds = 3)),
        )
}
