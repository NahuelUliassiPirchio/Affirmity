package com.pirxhio.affirmity.ui.meditation

import android.media.AudioManager
import androidx.lifecycle.Lifecycle
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionInterruptionPolicyTest {

    @Test
    fun `calls, alarms and other media pause the session`() {
        assertTrue(SessionInterruptionPolicy.shouldPauseFor(AudioManager.AUDIOFOCUS_LOSS))
        assertTrue(SessionInterruptionPolicy.shouldPauseFor(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT))
    }

    @Test
    fun `a short duckable sound such as a notification ping does not pause the session`() {
        assertFalse(SessionInterruptionPolicy.shouldPauseFor(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK))
    }

    @Test
    fun `gaining focus back never resumes the session`() {
        assertFalse(SessionInterruptionPolicy.shouldPauseFor(AudioManager.AUDIOFOCUS_GAIN))
        assertFalse(SessionInterruptionPolicy.shouldPauseFor(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT))
        assertFalse(SessionInterruptionPolicy.shouldPauseFor(0))
    }

    @Test
    fun `leaving the app pauses on stop and on nothing else`() {
        assertTrue(SessionInterruptionPolicy.shouldPauseFor(Lifecycle.Event.ON_STOP))
        Lifecycle.Event.entries.filter { it != Lifecycle.Event.ON_STOP }.forEach {
            assertFalse("$it", SessionInterruptionPolicy.shouldPauseFor(it))
        }
    }

    @Test
    fun `only a running session is paused`() {
        assertEquals(MeditationEvent.Pause, SessionInterruptionPolicy.pauseEventFor(SessionStatus.Running))
        listOf(SessionStatus.Idle, SessionStatus.Paused, SessionStatus.Completed, SessionStatus.Cancelled).forEach {
            assertNull("$it", SessionInterruptionPolicy.pauseEventFor(it))
        }
    }

    @Test
    fun `only a granted focus request allows playback`() {
        assertTrue(SessionInterruptionPolicy.isFocusGranted(AudioManager.AUDIOFOCUS_REQUEST_GRANTED))
        assertFalse(SessionInterruptionPolicy.isFocusGranted(AudioManager.AUDIOFOCUS_REQUEST_FAILED))
        assertFalse(SessionInterruptionPolicy.isFocusGranted(AudioManager.AUDIOFOCUS_REQUEST_DELAYED))
    }
}
