package com.pirxhio.affirmity.ui.meditation

import android.media.AudioManager
import androidx.lifecycle.Lifecycle
import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus

/**
 * When an outside interruption pauses a guided session. Never resumes: coming back is the user's
 * decision, through the regular resume control.
 */
object SessionInterruptionPolicy {
    /**
     * Calls, alarms and other media pause. A duckable loss (a notification ping, a navigation
     * prompt) does not: it is over in a moment, and pausing a breath hold for it would be worse.
     */
    fun shouldPauseFor(focusChange: Int): Boolean = when (focusChange) {
        AudioManager.AUDIOFOCUS_LOSS,
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
        -> true
        else -> false
    }

    /** App backgrounded or screen locked. */
    fun shouldPauseFor(event: Lifecycle.Event): Boolean = event == Lifecycle.Event.ON_STOP

    /** The event to send, or null when there is nothing to pause (idle, paused or finished). */
    fun pauseEventFor(status: SessionStatus): MeditationEvent? =
        if (status == SessionStatus.Running) MeditationEvent.Pause else null
}
