package com.pirxhio.affirmity.meditation.sohum

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Test

class SoHumMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs 67 breaths, each carrying the so-hum cue`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(soHumMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(134, phaseCount) // 67 breaths * 2 phases (inhale "so" + exhale "hum")
        assertEquals(67, executor.commands.filterIsInstance<ShowText>().count { it.textId == SoHumText.SO })
        assertEquals(67, executor.commands.filterIsInstance<ShowText>().count { it.textId == SoHumText.HUM })
    }
}
