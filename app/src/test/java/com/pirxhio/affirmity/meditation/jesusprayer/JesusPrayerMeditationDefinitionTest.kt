package com.pirxhio.affirmity.meditation.jesusprayer

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Test

class JesusPrayerMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config runs 67 breaths of inhale-exhale phrase pairs - 134 phases`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(jesusPrayerMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(134, phaseCount)
        assertEquals(67, executor.commands.count { it is ShowText && it.textId == JesusPrayerText.INHALE })
        assertEquals(67, executor.commands.count { it is ShowText && it.textId == JesusPrayerText.EXHALE })
    }

    @Test
    fun `custom breaths count is respected`() {
        val executor = RecordingCommandExecutor()
        val config = JesusPrayerConfig(breaths = 10)
        val engine = MeditationEngine(jesusPrayerMeditationDefinition(config), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(20, phaseCount)
    }
}
