package com.pirxhio.affirmity.meditation.dhikr

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Test

class DhikrMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs intention, 33 counted repetitions, then silence - 35 phases`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(dhikrMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(35, phaseCount) // intention + 33 repetitions + silence
        assertEquals(33, executor.commands.count { it is ShowText && it.textId == DhikrText.REPETITION })
    }

    @Test
    fun `custom repetitions count is respected`() {
        val executor = RecordingCommandExecutor()
        val config = DhikrConfig(repetitions = 11)
        val engine = MeditationEngine(dhikrMeditationDefinition(config), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(13, phaseCount) // intention + 11 repetitions + silence
    }
}
