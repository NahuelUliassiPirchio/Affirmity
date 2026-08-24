package com.pirxhio.affirmity.analytics

/** Hand-written recorder (project convention), records every `(name, params)` pair passed to it. */
class FakeFirebaseAnalyticsSink : FirebaseAnalyticsSink {
    data class Logged(val name: String, val params: List<AnalyticsParamValue>)

    private val _logged = mutableListOf<Logged>()
    val logged: List<Logged> get() = _logged

    override fun logEvent(name: String, params: List<AnalyticsParamValue>) {
        _logged.add(Logged(name, params))
    }
}
