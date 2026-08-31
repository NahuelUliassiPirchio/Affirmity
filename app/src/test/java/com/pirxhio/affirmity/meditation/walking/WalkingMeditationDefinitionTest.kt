package com.pirxhio.affirmity.meditation.walking

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Test

class WalkingMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs arrival, standing awareness, walking, closing - 4 phases with matching cues`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(walkingMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(4, phaseCount)
        assertEquals(
            listOf(
                WalkingMeditationText.ARRIVAL,
                WalkingMeditationText.STANDING,
                WalkingMeditationText.WALKING,
                WalkingMeditationText.CLOSING,
            ),
            executor.commands.filterIsInstance<ShowText>().map { it.textId },
        )
    }
}
