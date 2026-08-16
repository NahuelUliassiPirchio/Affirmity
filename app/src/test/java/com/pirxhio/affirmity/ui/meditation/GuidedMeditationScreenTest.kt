package com.pirxhio.affirmity.ui.meditation

import com.pirxhio.affirmity.meditation.MeditationCommand
import com.pirxhio.affirmity.meditation.MeditationCommandExecutor
import com.pirxhio.affirmity.meditation.MeditationEngine
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionEndReason
import com.pirxhio.affirmity.meditation.SessionStatus
import com.pirxhio.affirmity.meditation.breathing.BreathingConfig
import com.pirxhio.affirmity.meditation.breathing.breathingMeditationDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runtime coverage for [performGuidedSessionExit] (REQ-5.2, AC7) — closes the verify-report
 * CRITICAL gap: static inspection previously confirmed the call order, but no passing test drove
 * it. Runs against a real [MeditationEngine] (not a mock), the same class
 * [GuidedMeditationScreen]'s `requestExit` closure drives, proving cancellation is dispatched and
 * [SessionEndReason.Cancelled] is reported synchronously — before the function returns and before
 * [onExit] runs.
 */
class GuidedMeditationScreenTest {

    private val noOpExecutor = object : MeditationCommandExecutor {
        override fun execute(command: MeditationCommand) = Unit
    }

    private fun newEngine(): MeditationEngine =
        MeditationEngine(breathingMeditationDefinition(BreathingConfig()), noOpExecutor)

    @Test
    fun `exiting while Running synchronously cancels the engine and reports Cancelled before onExit`() {
        val engine = newEngine()
        engine.send(MeditationEvent.Start)
        assertEquals(SessionStatus.Running, engine.state.value.status)

        val calls = mutableListOf<String>()
        performGuidedSessionExit(
            status = engine.state.value.status,
            cancel = { engine.send(MeditationEvent.Cancel) },
            onSessionEnded = { reason -> calls.add("onSessionEnded:$reason") },
            onExit = { calls.add("onExit") },
        )

        // The engine itself is already terminal by the time performGuidedSessionExit returns —
        // MeditationEngine.send is synchronous (holds a plain monitor and updates state inline).
        assertEquals(SessionStatus.Cancelled, engine.state.value.status)
        assertEquals(listOf("onSessionEnded:${SessionEndReason.Cancelled}", "onExit"), calls)
    }

    @Test
    fun `exiting while Paused also cancels synchronously and reports Cancelled`() {
        val engine = newEngine()
        engine.send(MeditationEvent.Start)
        engine.send(MeditationEvent.Pause)
        assertEquals(SessionStatus.Paused, engine.state.value.status)

        val calls = mutableListOf<String>()
        performGuidedSessionExit(
            status = engine.state.value.status,
            cancel = { engine.send(MeditationEvent.Cancel) },
            onSessionEnded = { reason -> calls.add("onSessionEnded:$reason") },
            onExit = { calls.add("onExit") },
        )

        assertEquals(SessionStatus.Cancelled, engine.state.value.status)
        assertEquals(listOf("onSessionEnded:${SessionEndReason.Cancelled}", "onExit"), calls)
    }

    @Test
    fun `exiting from Idle never cancels and never reports a session end (EC-1)`() {
        val engine = newEngine()
        assertEquals(SessionStatus.Idle, engine.state.value.status)
        var cancelCalled = false

        val calls = mutableListOf<String>()
        performGuidedSessionExit(
            status = engine.state.value.status,
            cancel = { cancelCalled = true; engine.send(MeditationEvent.Cancel) },
            onSessionEnded = { reason -> calls.add("onSessionEnded:$reason") },
            onExit = { calls.add("onExit") },
        )

        assertTrue(!cancelCalled)
        assertEquals(listOf("onExit"), calls)
    }
}
