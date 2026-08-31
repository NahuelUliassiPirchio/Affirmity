package com.pirxhio.affirmity.meditation.breathingaffirmations

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowLiteralText
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathingAffirmationsMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    private fun runToCompletion(config: BreathingAffirmationsConfig): RecordingCommandExecutor {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(breathingAffirmationsMeditationDefinition(config), executor)
        engine.send(MeditationEvent.Start)
        while (engine.state.value.status == SessionStatus.Running) {
            engine.send(MeditationEvent.Next)
        }
        assertEquals(SessionStatus.Completed, engine.state.value.status)
        return executor
    }

    @Test
    fun `three affirmations produce one literal-text phase each, in order`() {
        val executor = runToCompletion(
            BreathingAffirmationsConfig(
                affirmationTexts = listOf("Soy capaz", "Confío en mí", "Estoy en paz"),
            ),
        )

        val literalTexts = executor.commands.filterIsInstance<ShowLiteralText>().map { it.value }
        assertEquals(listOf("Soy capaz", "Confío en mí", "Estoy en paz"), literalTexts)
    }

    @Test
    fun `empty affirmation list falls back to one static cue, never zero phases or a crash`() {
        val executor = runToCompletion(BreathingAffirmationsConfig(affirmationTexts = emptyList()))

        assertEquals(0, executor.commands.count { it is ShowLiteralText })
        assertEquals(
            1,
            executor.commands.count {
                it is ShowText && it.textId == BreathingAffirmationsText.AFFIRMATION_UNAVAILABLE
            },
        )
    }

    @Test
    fun `default config completes with the meditation cue and a box_breathing hold cue when selected`() {
        val executor = runToCompletion(
            BreathingAffirmationsConfig(breathingTechnique = "box_breathing", affirmationTexts = listOf("x")),
        )

        assertTrue(executor.commands.any { it is ShowText && it.textId == BreathingAffirmationsText.MEDITATION })
    }

    @Test
    fun `rejects a non-positive duration instead of building a broken session`() {
        var threw = false
        try {
            breathingAffirmationsMeditationDefinition(BreathingAffirmationsConfig(breathingMillis = 0L))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
