package com.pirxhio.affirmity.diagnostics

import com.pirxhio.affirmity.analytics.AnalyticsConsentState
import org.junit.Assert.assertEquals
import org.junit.Test

class CrashReportingConsentTest {
    private class FakeCollection : CrashlyticsCollection {
        val calls = mutableListOf<String>()
        override fun setEnabled(enabled: Boolean) { calls += "enabled=$enabled" }
        override fun deleteUnsentReports() { calls += "deleteUnsent" }
    }

    @Test fun `granted consent enables collection in a release build`() {
        val collection = FakeCollection()
        CrashReportingConsent(collection, isDebugBuild = false).apply(AnalyticsConsentState.GRANTED)
        assertEquals(listOf("enabled=true"), collection.calls)
    }

    @Test fun `denied and unknown consent disable collection and delete unsent reports`() {
        listOf(AnalyticsConsentState.DENIED, AnalyticsConsentState.UNKNOWN).forEach { state ->
            val collection = FakeCollection()
            CrashReportingConsent(collection, isDebugBuild = false).apply(state)
            assertEquals(state.name, listOf("enabled=false", "deleteUnsent"), collection.calls)
        }
    }

    @Test fun `revoking a granted consent disables collection and deletes unsent reports`() {
        val collection = FakeCollection()
        val consent = CrashReportingConsent(collection, isDebugBuild = false)
        consent.apply(AnalyticsConsentState.GRANTED)
        consent.apply(AnalyticsConsentState.DENIED)
        assertEquals(listOf("enabled=true", "enabled=false", "deleteUnsent"), collection.calls)
    }

    @Test fun `debug builds never enable collection even when consent is granted`() {
        val collection = FakeCollection()
        CrashReportingConsent(collection, isDebugBuild = true).apply(AnalyticsConsentState.GRANTED)
        assertEquals(listOf("enabled=false", "deleteUnsent"), collection.calls)
    }
}
