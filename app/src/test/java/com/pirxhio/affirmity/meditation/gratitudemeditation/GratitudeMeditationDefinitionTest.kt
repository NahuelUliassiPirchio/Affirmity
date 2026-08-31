package com.pirxhio.affirmity.meditation.gratitudemeditation

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Test

class GratitudeMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs arrival, then 3 reflection prompts - 4 phases`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(gratitudeMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(4, phaseCount)
        assertEquals(1, executor.commands.count { it is ShowText && it.textId == GratitudeMeditationText.PERSON })
        assertEquals(1, executor.commands.count { it is ShowText && it.textId == GratitudeMeditationText.EXPERIENCE })
        assertEquals(1, executor.commands.count { it is ShowText && it.textId == GratitudeMeditationText.PRESENT })
    }

    @Test
    fun `promptCount of 1 runs only arrival and person - 2 phases`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(
            gratitudeMeditationDefinition(GratitudeConfig(promptCount = 1)),
            executor,
        )
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(2, phaseCount)
        assertEquals(1, executor.commands.count { it is ShowText && it.textId == GratitudeMeditationText.PERSON })
        assertEquals(0, executor.commands.count { it is ShowText && it.textId == GratitudeMeditationText.EXPERIENCE })
        assertEquals(0, executor.commands.count { it is ShowText && it.textId == GratitudeMeditationText.PRESENT })
    }

    @Test
    fun `promptCount of 2 runs arrival, person, experience - 3 phases`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(
            gratitudeMeditationDefinition(GratitudeConfig(promptCount = 2)),
            executor,
        )
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(3, phaseCount)
    }
}
