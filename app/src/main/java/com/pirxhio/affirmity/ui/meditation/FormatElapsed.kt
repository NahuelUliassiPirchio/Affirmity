package com.pirxhio.affirmity.ui.meditation

/**
 * Elapsed time as `m:ss` ("0:07", "1:30", "4:05"); over an hour keeps counting
 * total minutes ("75:00"), never an hours field; negative input is treated as zero.
 */
fun formatElapsed(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) / 1000L)
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
