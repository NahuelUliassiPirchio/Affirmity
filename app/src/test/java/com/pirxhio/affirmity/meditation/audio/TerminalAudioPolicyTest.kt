package com.pirxhio.affirmity.meditation.audio

import com.pirxhio.affirmity.meditation.SessionEndReason
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalAudioPolicyTest {

    @Test
    fun `cancellation stops every playing id immediately`() {
        val actions = TerminalAudioPolicy.plan(
            reason = SessionEndReason.Cancelled,
            playingAudioIds = setOf("bed", "voice"),
            rampingToSilenceAudioIds = emptySet(),
        )

        assertEquals(
            listOf(TerminalAudioAction.StopNow("bed"), TerminalAudioAction.StopNow("voice")),
            actions,
        )
    }

    @Test
    fun `graceful completion fades out everything still playing when nothing is already ramping to silence`() {
        val actions = TerminalAudioPolicy.plan(
            reason = SessionEndReason.Completed,
            playingAudioIds = setOf("bed"),
            rampingToSilenceAudioIds = emptySet(),
        )

        assertEquals(
            listOf(TerminalAudioAction.FadeOut("bed", TerminalAudioPolicy.GRACEFUL_END_FADE_MILLIS)),
            actions,
        )
    }

    @Test
    fun `graceful completion leaves an id already ramping to silence strictly alone`() {
        val actions = TerminalAudioPolicy.plan(
            reason = SessionEndReason.Completed,
            playingAudioIds = setOf("bed"),
            rampingToSilenceAudioIds = setOf("bed"),
        )

        assertEquals(emptyList<TerminalAudioAction>(), actions)
    }

    @Test
    fun `graceful completion with a mix only fades out the ids not already ramping to silence`() {
        val actions = TerminalAudioPolicy.plan(
            reason = SessionEndReason.Completed,
            playingAudioIds = setOf("bed", "voice"),
            rampingToSilenceAudioIds = setOf("bed"),
        )

        assertEquals(
            listOf(TerminalAudioAction.FadeOut("voice", TerminalAudioPolicy.GRACEFUL_END_FADE_MILLIS)),
            actions,
        )
    }
}
