package com.pirxhio.affirmity.analytics

/**
 * Default-DENY decorator (PD-1, REQ-4.4). Nothing reaches [delegate] unless [consentState] reads
 * [AnalyticsConsentState.GRANTED] at the moment of the call — [AnalyticsConsentState.UNKNOWN] and
 * [AnalyticsConsentState.DENIED] both fully suppress delegation, no partial/sampled emission.
 */
class ConsentGatedAnalyticsLogger(
    private val delegate: AnalyticsLogger,
    private val consentState: () -> AnalyticsConsentState,
) : AnalyticsLogger {
    override fun log(event: AnalyticsEvent) {
        if (consentState() == AnalyticsConsentState.GRANTED) {
            delegate.log(event)
        }
    }
}
