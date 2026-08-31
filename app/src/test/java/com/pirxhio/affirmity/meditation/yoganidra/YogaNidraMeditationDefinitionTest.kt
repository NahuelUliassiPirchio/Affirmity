package com.pirxhio.affirmity.meditation.yoganidra

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class YogaNidraMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `runs settling, intention, six-region body rotation, breath awareness, visualization, then return - 11 phases`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(yogaNidraMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        // settling + intention + 6 body regions + breath-awareness + visualization + return
        assertEquals(11, phaseCount)
    }
}
