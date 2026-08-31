package com.pirxhio.affirmity.meditation.breathoffire

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.StartAmbient
import com.pirxhio.affirmity.meditation.StopAmbient
import org.junit.Assert.assertEquals
import org.junit.Test

class BreathOfFireMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs 3 rounds of preparation-fire breath-recovery, 9 phases, ambient started and stopped once per round`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(breathOfFireMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(9, phaseCount) // 3 rounds * 3 phases
        assertEquals(3, executor.commands.count { it is StartAmbient })
        assertEquals(3, executor.commands.count { it is StopAmbient })
    }
}
