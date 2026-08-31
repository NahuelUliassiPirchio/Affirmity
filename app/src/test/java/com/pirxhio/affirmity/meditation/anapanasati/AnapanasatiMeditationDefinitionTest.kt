package com.pirxhio.affirmity.meditation.anapanasati

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Test

class AnapanasatiMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs arrival, breath awareness, closing - 3 phases with matching cues`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(anapanasatiMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(3, phaseCount)
        assertEquals(
            listOf(AnapanasatiText.ARRIVAL, AnapanasatiText.AWARENESS, AnapanasatiText.CLOSING),
            executor.commands.filterIsInstance<ShowText>().map { it.textId },
        )
    }
}
