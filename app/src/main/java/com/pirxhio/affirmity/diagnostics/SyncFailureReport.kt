package com.pirxhio.affirmity.diagnostics

import com.google.firebase.firestore.FirebaseFirestoreException

enum class TerminalRowKind(val wireName: String) {
    EVENT("event"),
    COVERAGE("coverage"),
}

/**
 * Everything a sync-failure report may carry. Deliberately has no message, uid, document path or event id:
 * only class names and enum-like codes, so nothing identifying can reach a crash backend.
 */
data class SyncFailureReport(
    val kind: SyncFailureKind,
    val errorClass: String,
    val firestoreCode: String? = null,
    val rowKind: TerminalRowKind? = null,
)

/** Keeps only the class name and Firestore code of [failure], never its message. */
fun syncFailureReport(
    kind: SyncFailureKind,
    failure: Throwable,
    rowKind: TerminalRowKind? = null,
): SyncFailureReport = SyncFailureReport(
    kind = kind,
    errorClass = failure.javaClass.name,
    firestoreCode = (failure as? FirebaseFirestoreException)?.code?.name,
    rowKind = rowKind,
)
