package com.pirxhio.affirmity.diagnostics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pirxhio.affirmity.analytics.AnalyticsConsentState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any

class ProcessDiagnosticsTest {
    private class FakeCollection : CrashlyticsCollection {
        val calls = mutableListOf<String>()
        override fun setEnabled(enabled: Boolean) { calls += "enabled=$enabled" }
        override fun deleteUnsentReports() { calls += "deleteUnsent" }
    }

    @Test fun `the shared holder always hands out the same reporter instance`() {
        assertSame(ProcessDiagnostics.shared.syncFailureReporter, ProcessDiagnostics.shared.syncFailureReporter)
    }

    @Test fun `dedup holds across two consumers of the same process holder`() {
        val crashlytics = Mockito.mock(FirebaseCrashlytics::class.java)
        val diagnostics = ProcessDiagnostics { crashlytics }
        val first = diagnostics.syncFailureReporter // e.g. the first Activity's AppState
        val second = diagnostics.syncFailureReporter // e.g. the recreated Activity's AppState
        val report = SyncFailureReport(SyncFailureKind.ACTIVITY_SAVE, "java.io.IOException")
        first.report(report)
        second.report(report)
        Mockito.verify(crashlytics, Mockito.times(1)).recordException(any())
    }

    @Test fun `startup consent with the current unknown signal disables collection and deletes unsent reports`() {
        val collection = FakeCollection()
        ProcessDiagnostics.shared.applyStartupConsent(collection, isDebugBuild = false)
        assertEquals(listOf("enabled=false", "deleteUnsent"), collection.calls)
    }

    @Test fun `startup consent never enables collection in a debug build`() {
        val collection = FakeCollection()
        ProcessDiagnostics { Mockito.mock(FirebaseCrashlytics::class.java) }
            .applyStartupConsent(collection, isDebugBuild = true, consent = { AnalyticsConsentState.GRANTED })
        assertEquals(listOf("enabled=false", "deleteUnsent"), collection.calls)
    }

    @Test fun `startup consent enables collection in a release build once granted`() {
        val collection = FakeCollection()
        ProcessDiagnostics.shared.applyStartupConsent(collection, isDebugBuild = false, consent = { AnalyticsConsentState.GRANTED })
        assertEquals(listOf("enabled=true"), collection.calls)
    }
}
