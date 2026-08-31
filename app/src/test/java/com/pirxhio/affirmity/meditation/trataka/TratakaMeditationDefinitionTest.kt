package com.pirxhio.affirmity.meditation.trataka

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Test

class TratakaMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs preparation, 2 rounds of focus+afterimage, then rest - 6 phases`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(tratakaMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(6, phaseCount) // preparation + 2*(external_focus + eyes_closed) + rest
        assertEquals(2, executor.commands.count { it is ShowText && it.textId == TratakaText.EXTERNAL_FOCUS })
    }

    @Test
    fun `custom rounds count is respected`() {
        val executor = RecordingCommandExecutor()
        val config = TratakaConfig(rounds = 4)
        val engine = MeditationEngine(tratakaMeditationDefinition(config), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(10, phaseCount) // preparation + 4*2 + rest
    }
}
