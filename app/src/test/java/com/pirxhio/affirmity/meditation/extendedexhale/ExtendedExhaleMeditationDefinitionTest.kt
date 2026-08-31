package com.pirxhio.affirmity.meditation.extendedexhale

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ExtendedExhaleMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs 10 rounds of inhale-exhale, 20 phases total`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(extendedExhaleMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)
        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }
        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(20, phaseCount)
    }
}
