package com.pirxhio.affirmity.diagnostics

import java.util.concurrent.ConcurrentHashMap

/**
 * Forwards each distinct failure once per process run. Terminal uploads are keyed by Firestore code (or error
 * class), so a re-armed poison row cannot spam; other kinds by kind + error class + code + row kind, so an early transient
 * error cannot mask a different systematic one.
 */
class OncePerRunSyncFailureReporter(private val delegate: SyncFailureReporter) : SyncFailureReporter {
    private val seen: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun report(report: SyncFailureReport) {
        val key = when (report.kind) {
            SyncFailureKind.TERMINAL_UPLOAD -> "${report.kind.wireName}:${report.firestoreCode ?: report.errorClass}"
            else -> "${report.kind.wireName}:${report.errorClass}:${report.firestoreCode}:${report.rowKind}"
        }
        if (seen.add(key)) delegate.report(report)
    }
}
