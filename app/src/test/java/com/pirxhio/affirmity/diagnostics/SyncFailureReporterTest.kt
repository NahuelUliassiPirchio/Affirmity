package com.pirxhio.affirmity.diagnostics

import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SyncFailureReporterTest {
    private val secretMessage = "PERMISSION_DENIED: users/uid-SECRET/activity/evt-SECRET"

    @Test fun `report built from a Firestore failure carries class name and code only`() {
        val report = syncFailureReport(SyncFailureKind.RETRY_LOOP,
            FirebaseFirestoreException(secretMessage, Code.PERMISSION_DENIED))
        assertEquals("com.google.firebase.firestore.FirebaseFirestoreException", report.errorClass)
        assertEquals("PERMISSION_DENIED", report.firestoreCode)
        assertFalse(report.toString().contains("SECRET"))
        assertFalse(report.toString().contains("users/"))
    }

    @Test fun `report built from a plain failure has no Firestore code and never keeps the message`() {
        val report = syncFailureReport(SyncFailureKind.ACTIVITY_SAVE, IllegalStateException(secretMessage))
        assertEquals("java.lang.IllegalStateException", report.errorClass)
        assertNull(report.firestoreCode)
        assertFalse(report.toString().contains("SECRET"))
    }

    @Test fun `once-per-run reporter forwards identical retry loop reports only once`() {
        val fake = FakeSyncFailureReporter()
        val limited = OncePerRunSyncFailureReporter(fake)
        repeat(3) { limited.report(SyncFailureReport(SyncFailureKind.RETRY_LOOP, "A", "UNKNOWN")) }
        assertEquals(1, fake.reports.size)
    }

    @Test fun `a different error class or code of the same kind is reported again`() {
        val fake = FakeSyncFailureReporter()
        val limited = OncePerRunSyncFailureReporter(fake)
        limited.report(SyncFailureReport(SyncFailureKind.CLAIM, "java.io.IOException"))
        limited.report(SyncFailureReport(SyncFailureKind.CLAIM, "java.io.IOException"))
        limited.report(SyncFailureReport(SyncFailureKind.CLAIM, "java.lang.IllegalStateException"))
        limited.report(SyncFailureReport(SyncFailureKind.CLAIM, "com.x.Firestore", "ABORTED"))
        assertEquals(listOf("java.io.IOException", "java.lang.IllegalStateException", "com.x.Firestore"),
            fake.reports.map { it.errorClass })
    }

    @Test fun `terminal upload reports are limited per code or reason regardless of row kind`() {
        val fake = FakeSyncFailureReporter()
        val limited = OncePerRunSyncFailureReporter(fake)
        limited.report(SyncFailureReport(SyncFailureKind.TERMINAL_UPLOAD, "X", "PERMISSION_DENIED", TerminalRowKind.EVENT))
        limited.report(SyncFailureReport(SyncFailureKind.TERMINAL_UPLOAD, "Y", "PERMISSION_DENIED", TerminalRowKind.COVERAGE))
        limited.report(SyncFailureReport(SyncFailureKind.TERMINAL_UPLOAD, "X", "INVALID_ARGUMENT", TerminalRowKind.EVENT))
        limited.report(SyncFailureReport(SyncFailureKind.TERMINAL_UPLOAD, "IllegalArgumentException", null, TerminalRowKind.EVENT))
        limited.report(SyncFailureReport(SyncFailureKind.TERMINAL_UPLOAD, "IllegalArgumentException", null, TerminalRowKind.EVENT))
        assertEquals(listOf("PERMISSION_DENIED", "INVALID_ARGUMENT", null), fake.reports.map { it.firestoreCode })
    }

    @Test fun `each kind is limited independently`() {
        val fake = FakeSyncFailureReporter()
        val limited = OncePerRunSyncFailureReporter(fake)
        repeat(2) { SyncFailureKind.entries.forEach { limited.report(SyncFailureReport(it, "A")) } }
        assertEquals(SyncFailureKind.entries, fake.reports.map { it.kind })
    }

    @Test fun `safeReport swallows reporter failures`() {
        val throwing = FakeSyncFailureReporter(IllegalStateException("sdk not initialized"))
        throwing.safeReport(SyncFailureReport(SyncFailureKind.CLAIM, "A"))
        assertEquals(1, throwing.reports.size)
    }

    @Test fun `safeReport rethrows cancellation`() {
        val cancelling = FakeSyncFailureReporter(CancellationException("stop"))
        try {
            cancelling.safeReport(SyncFailureReport(SyncFailureKind.CLAIM, "A"))
            fail("expected cancellation")
        } catch (_: CancellationException) {
            assertTrue(true)
        }
    }
}
