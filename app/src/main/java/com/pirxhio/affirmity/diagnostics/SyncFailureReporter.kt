package com.pirxhio.affirmity.diagnostics

import kotlinx.coroutines.CancellationException

/** Fire-and-forget, like [com.pirxhio.affirmity.analytics.AnalyticsLogger]: call sites use [safeReport]. */
interface SyncFailureReporter {
    fun report(report: SyncFailureReport)
}

object NoOpSyncFailureReporter : SyncFailureReporter {
    override fun report(report: SyncFailureReport) {}
}

/** Reporting must never change sync or save behavior: swallows everything except cancellation. */
fun SyncFailureReporter.safeReport(report: SyncFailureReport) {
    try {
        report(report)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        // Diagnostics only.
    }
}
