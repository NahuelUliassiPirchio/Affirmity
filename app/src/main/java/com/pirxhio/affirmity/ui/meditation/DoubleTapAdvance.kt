package com.pirxhio.affirmity.ui.meditation

import com.pirxhio.affirmity.meditation.MeditationEvent
import com.pirxhio.affirmity.meditation.SessionStatus

/**
 * What a double tap on the session screen should send, or `null` for nothing. Only a Running
 * session of an entry that opts in advances; Paused, Idle and finished sessions ignore it. The
 * event is the same one the Skip button sends (it leaves a whole marked breathing block, or
 * advances one phase elsewhere); the button stays on screen for accessibility.
 */
fun doubleTapAdvanceEvent(status: SessionStatus, doubleTapAdvances: Boolean): MeditationEvent? =
    if (doubleTapAdvances && status == SessionStatus.Running) MeditationEvent.Skip else null
