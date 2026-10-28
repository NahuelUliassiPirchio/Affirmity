package com.pirxhio.affirmity.diagnostics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.kotlin.any

class CrashlyticsSyncFailureReporterTest {
    private val secret = FirebaseFirestoreException("users/uid-SECRET/activity/evt-SECRET", Code.PERMISSION_DENIED)

    @Test fun `recorded throwable carries no uid, document path or raw message and no keys are set`() {
        val crashlytics = Mockito.mock(FirebaseCrashlytics::class.java)
        CrashlyticsSyncFailureReporter { crashlytics }.report(
            syncFailureReport(SyncFailureKind.TERMINAL_UPLOAD, secret, TerminalRowKind.EVENT))

        val captor = ArgumentCaptor.forClass(Throwable::class.java)
        verify(crashlytics).recordException(captor.capture())
        val recorded = captor.value
        assertNotNull(recorded)
        val text = recorded.message.orEmpty() + recorded.javaClass.name + recorded.cause + recorded.stackTrace.size
        listOf("uid-SECRET", "evt-SECRET", "users/", "PERMISSION_DENIED: users").forEach {
            assertFalse("leaked $it", text.contains(it))
        }
        assertEquals("sync_failure kind=terminal_upload error=com.google.firebase.firestore.FirebaseFirestoreException " +
            "code=PERMISSION_DENIED row=event", recorded.message)
        verify(crashlytics, never()).setUserId(any())
        verifyNoMoreInteractions(crashlytics) // no custom keys either
    }

    @Test fun `a throwing recordException is swallowed`() {
        val crashlytics = Mockito.mock(FirebaseCrashlytics::class.java)
        Mockito.doThrow(IllegalStateException("boom")).`when`(crashlytics).recordException(any())
        CrashlyticsSyncFailureReporter { crashlytics }.report(SyncFailureReport(SyncFailureKind.CLAIM, "A"))
    }

    @Test fun `an uninitialized Firebase instance is swallowed and only resolved at report time`() {
        var resolved = 0
        val reporter = CrashlyticsSyncFailureReporter { resolved++; throw IllegalStateException("FirebaseApp not initialized") }
        assertEquals(0, resolved)
        reporter.report(SyncFailureReport(SyncFailureKind.CLAIM, "A"))
        assertEquals(1, resolved)
    }

    @Test fun `factory wraps Crashlytics so a repeated failure is recorded once per run`() {
        val crashlytics = Mockito.mock(FirebaseCrashlytics::class.java)
        val reporter = createSyncFailureReporter { crashlytics }
        repeat(3) { reporter.report(SyncFailureReport(SyncFailureKind.ACTIVITY_SAVE, "java.io.IOException")) }
        verify(crashlytics, Mockito.times(1)).recordException(any())
    }
}
