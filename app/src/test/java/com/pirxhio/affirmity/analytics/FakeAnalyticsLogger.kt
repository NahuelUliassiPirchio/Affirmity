package com.pirxhio.affirmity.analytics

/** Hand-written recorder (project convention: hand-written fakes over Mockito, REQ-6.1). */
class FakeAnalyticsLogger : AnalyticsLogger {
    private val _recorded = mutableListOf<AnalyticsEvent>()
    val recorded: List<AnalyticsEvent> get() = _recorded

    override fun log(event: AnalyticsEvent) {
        _recorded.add(event)
    }
}
