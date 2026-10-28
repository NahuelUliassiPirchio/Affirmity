package com.pirxhio.affirmity.diagnostics

class FakeSyncFailureReporter(private val failure: RuntimeException? = null) : SyncFailureReporter {
    private val _reports = mutableListOf<SyncFailureReport>()
    val reports: List<SyncFailureReport> get() = _reports

    override fun report(report: SyncFailureReport) {
        _reports.add(report)
        failure?.let { throw it }
    }
}
