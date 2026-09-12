package com.pirxhio.affirmity.personalization.signal

/**
 * Default DI binding until Slice 2 wires real emission call sites (design "Migration / Rollout").
 * Keeps every existing feature functional with zero personalization side effects in the meantime.
 */
object NoOpPersonalizationSignalRecorder : PersonalizationSignalRecorder {
    override fun record(signal: PersonalizationSignal) = Unit
}
