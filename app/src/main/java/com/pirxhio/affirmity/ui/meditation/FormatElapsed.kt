package com.pirxhio.affirmity.ui.meditation

/**
 * Elapsed time as `mm:ss` ("00:07", "01:30", "04:05"); over an hour keeps counting
 * total minutes ("75:00"), never an hours field; negative input is treated as zero.
 */
fun formatElapsed(millis: Long): String = formatClock(millis.coerceAtLeast(0L) / 1000L)

/**
 * Countdown label for the time left in a phase as `mm:ss` ("00:30"): the second currently being counted down,
 * so it reads "00:01" (not "00:00") until the phase ends; negative input is treated as zero.
 */
fun formatRemainingSeconds(remainingMillis: Long): String =
    formatClock(remainingMillis.coerceAtLeast(0L) / 1000L + 1L)

private fun formatClock(totalSeconds: Long): String =
    "${(totalSeconds / 60).toString().padStart(2, '0')}:${(totalSeconds % 60).toString().padStart(2, '0')}"
