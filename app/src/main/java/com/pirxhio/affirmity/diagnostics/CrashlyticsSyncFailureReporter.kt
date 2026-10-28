package com.pirxhio.affirmity.diagnostics

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Crashlytics-backed sink. Records a synthetic exception built only from the report's enum-like fields:
 * Firestore messages can embed document paths (`users/{uid}/...`), so the original throwable is never recorded.
 * No custom keys (they are sticky across reports) and no `setUserId`. [crashlytics] is resolved per report so
 * an uninitialized FirebaseApp cannot break construction.
 */
class CrashlyticsSyncFailureReporter(private val crashlytics: () -> FirebaseCrashlytics) : SyncFailureReporter {
    override fun report(report: SyncFailureReport) {
        try {
            crashlytics().recordException(SyncFailureException(report))
        } catch (_: RuntimeException) {
            // Diagnostics must never take the sync path down.
        }
    }
}

/** Synthetic, PII-free throwable: the message is built from [SyncFailureReport] fields only. */
class SyncFailureException(report: SyncFailureReport) : RuntimeException(
    "sync_failure kind=${report.kind.wireName} error=${report.errorClass}" +
        (report.firestoreCode?.let { " code=$it" } ?: "") +
        (report.rowKind?.let { " row=${it.wireName}" } ?: ""),
)
