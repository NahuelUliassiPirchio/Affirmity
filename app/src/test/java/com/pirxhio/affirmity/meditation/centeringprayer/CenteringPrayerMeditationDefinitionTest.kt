package com.pirxhio.affirmity.meditation.centeringprayer

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CenteringPrayerMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs sacred word, silence, closing - 3 phases, silence carries no cue`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(centeringPrayerMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(3, phaseCount)
        assertTrue(executor.commands.none { it is ShowText && it.textId !in setOf(CenteringPrayerText.SACRED_WORD, CenteringPrayerText.CLOSING) })
    }

    @Test
    fun `includeSacredWord false omits the sacred word phase - 2 phases`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(
            centeringPrayerMeditationDefinition(CenteringPrayerConfig(includeSacredWord = false)),
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
        assertEquals(0, executor.commands.count { it is ShowText && it.textId == CenteringPrayerText.SACRED_WORD })
    }
}
