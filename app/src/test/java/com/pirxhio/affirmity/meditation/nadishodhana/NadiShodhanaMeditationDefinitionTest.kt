package com.pirxhio.affirmity.meditation.nadishodhana

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.ShowText
import org.junit.Assert.assertEquals
import org.junit.Test

class NadiShodhanaMeditationDefinitionTest {

    private class RecordingCommandExecutor : MeditationCommandExecutor {
        val commands: MutableList<MeditationCommand> = mutableListOf()
        override fun execute(command: MeditationCommand) {
            commands.add(command)
        }
    }

    @Test
    fun `default config cycles inhale-left, exhale-right, inhale-right, exhale-left, 6 times = 24 phases`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(nadiShodhanaMeditationDefinition(), executor)
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(24, phaseCount) // 6 rounds * 4 phases
        assertEquals(
            listOf(
                NadiShodhanaText.INHALE_LEFT,
                NadiShodhanaText.EXHALE_RIGHT,
                NadiShodhanaText.INHALE_RIGHT,
                NadiShodhanaText.EXHALE_LEFT,
            ),
            executor.commands.filterIsInstance<ShowText>().take(4).map { it.textId },
        )
    }

    @Test
    fun `retention inserts a hold phase after each inhale, 6 phases per round`() {
        val executor = RecordingCommandExecutor()
        val engine = MeditationEngine(
            nadiShodhanaMeditationDefinition(NadiShodhanaConfig(rounds = 1, retention = true)),
            executor,
        )
        engine.send(MeditationEvent.Start)

        var phaseCount = 0
        while (engine.state.value.status == SessionStatus.Running) {
            phaseCount++
            engine.send(MeditationEvent.Next)
        }

        assertEquals(SessionStatus.Completed, engine.state.value.status)
        assertEquals(6, phaseCount) // inhale, hold, exhale, inhale, hold, exhale
        assertEquals(
            listOf(
                NadiShodhanaText.INHALE_LEFT,
                NadiShodhanaText.HOLD,
                NadiShodhanaText.EXHALE_RIGHT,
                NadiShodhanaText.INHALE_RIGHT,
                NadiShodhanaText.HOLD,
                NadiShodhanaText.EXHALE_LEFT,
            ),
            executor.commands.filterIsInstance<ShowText>().map { it.textId },
        )
    }
}
