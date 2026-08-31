package com.pirxhio.affirmity.meditation.metta

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Test

class MettaMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs the 5 targets in spec order`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(mettaMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(5, phaseCount)
        assertEquals(
            listOf(
                MettaText.SELF,
                MettaText.LOVED_ONE,
                MettaText.NEUTRAL_PERSON,
                MettaText.DIFFICULT_PERSON,
                MettaText.ALL_BEINGS,
            ),
            executor.commands.filterIsInstance<ShowText>().map { it.textId },
        )
    }
}
