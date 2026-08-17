package com.pirxhio.affirmity.analytics

/**
 * Classifies [com.pirxhio.affirmity.access.AdUnlockOutcome.Failed.reason] into a bounded enum
 * before it may reach an [AnalyticsEvent] (D2.3, REQ-4.3). The raw vendor string keeps going only
 * to the existing `Log.w` call — it never crosses this boundary.
 */
internal fun String.toAdFailureReason(): AdFailureReason = when {
    contains("no fill", ignoreCase = true) || contains("no_fill", ignoreCase = true) -> AdFailureReason.NO_FILL
    contains("network", ignoreCase = true) -> AdFailureReason.NETWORK
    contains("timeout", ignoreCase = true) || contains("timed out", ignoreCase = true) -> AdFailureReason.TIMEOUT
    contains("show", ignoreCase = true) -> AdFailureReason.SHOW_FAILED
    contains("config", ignoreCase = true) || contains("invalid", ignoreCase = true) -> AdFailureReason.CONFIG
    else -> AdFailureReason.UNKNOWN
}
