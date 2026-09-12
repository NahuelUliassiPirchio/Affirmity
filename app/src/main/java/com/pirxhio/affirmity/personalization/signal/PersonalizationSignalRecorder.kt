package com.pirxhio.affirmity.personalization.signal

/**
 * Product functionality, not telemetry (spec "Signal emission independent of analytics consent",
 * design D3). MUST NEVER be wrapped by a consent-gated decorator -- analytics consent must never
 * suppress personalization signal recording. Non-suspend, fire-and-forget: same frozen shape as
 * `AnalyticsLogger`, so call sites stay one-line and contain no direct Room/DAO logic.
 */
interface PersonalizationSignalRecorder {
    fun record(signal: PersonalizationSignal)
}
