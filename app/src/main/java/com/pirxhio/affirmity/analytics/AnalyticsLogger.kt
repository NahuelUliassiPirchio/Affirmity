package com.pirxhio.affirmity.analytics

/**
 * The one frozen seam (REQ-4.1). MUST NOT gain a `Context`/`Activity`/callback parameter, MUST
 * NOT gain a `suspend` modifier, and MUST NOT gain a `logEvent(name: String, params: Map<...>)`
 * escape hatch. Every call site fires-and-forgets synchronously.
 */
interface AnalyticsLogger {
    fun log(event: AnalyticsEvent)
}

/** Default wiring until consent is granted and the composition root swaps the delegate (D5). */
object NoOpAnalyticsLogger : AnalyticsLogger {
    override fun log(event: AnalyticsEvent) {}
}

/** PD-1: exactly three cases, default is always [UNKNOWN] — never [GRANTED] at construction. */
enum class AnalyticsConsentState { GRANTED, DENIED, UNKNOWN }
