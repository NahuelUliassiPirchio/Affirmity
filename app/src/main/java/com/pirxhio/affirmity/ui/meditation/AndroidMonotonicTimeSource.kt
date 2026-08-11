package com.pirxhio.affirmity.ui.meditation

import android.os.SystemClock
import com.pirxhio.affirmity.meditation.MonotonicTimeSource

/** `elapsedRealtime` keeps advancing while the device sleeps (unlike `uptimeMillis`) but is
 * immune to wall-clock/timezone changes (unlike `currentTimeMillis`) — the right source for a
 * session timer that must survive being backgrounded. Kept out of the `meditation` package so the
 * domain stays Android-free (see [com.pirxhio.affirmity.meditation.MonotonicTimeSource]). */
val AndroidMonotonicTimeSource = MonotonicTimeSource { SystemClock.elapsedRealtime() }
