package com.pirxhio.affirmity.meditation.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CuePlaybackGuardTest {

    private val guard = CuePlaybackGuard()

    @Test
    fun `first request for a cue in a phase entry plays`() {
        assertTrue(guard.shouldPlay(MeditationCue.RoundStart, entryKey = 1))
    }

    @Test
    fun `same cue twice within the same phase entry is blocked`() {
        guard.shouldPlay(MeditationCue.RoundStart, entryKey = 1)

        assertFalse(guard.shouldPlay(MeditationCue.RoundStart, entryKey = 1))
    }

    @Test
    fun `same cue in a later phase entry plays again`() {
        guard.shouldPlay(MeditationCue.RoundStart, entryKey = 1)

        assertTrue(guard.shouldPlay(MeditationCue.RoundStart, entryKey = 2))
    }

    @Test
    fun `different cues within the same phase entry both play`() {
        guard.shouldPlay(MeditationCue.RoundEnd, entryKey = 3)

        assertTrue(guard.shouldPlay(MeditationCue.RoundStart, entryKey = 3))
    }

    @Test
    fun `unkeyed requests always play`() {
        guard.shouldPlay(MeditationCue.SectionTransition, entryKey = null)

        assertTrue(guard.shouldPlay(MeditationCue.SectionTransition, entryKey = null))
    }

    @Test
    fun `reset forgets everything`() {
        guard.shouldPlay(MeditationCue.SessionStart, entryKey = 1)
        guard.reset()

        assertTrue(guard.shouldPlay(MeditationCue.SessionStart, entryKey = 1))
    }
}
