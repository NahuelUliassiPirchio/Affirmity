package com.pirxhio.affirmity.diagnostics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pirxhio.affirmity.analytics.AnalyticsConsentState

/**
 * Process-scoped diagnostics: one reporter, so "once per run" survives Activity recreation, and one consent
 * entry point that runs at process start, so headless starts (widget, FCM) also clean up.
 */
class ProcessDiagnostics(crashlytics: () -> FirebaseCrashlytics = { FirebaseCrashlytics.getInstance() }) {
    private val crashlyticsProvider = crashlytics

    val syncFailureReporter: SyncFailureReporter by lazy { createSyncFailureReporter(crashlyticsProvider) }

    /**
     * Applies [consent] to Crashlytics collection. Call from `Application.onCreate`, and again whenever the
     * consent state changes.
     */
    fun applyStartupConsent(
        collection: CrashlyticsCollection = FirebaseCrashlyticsCollection(crashlyticsProvider),
        isDebugBuild: Boolean,
        consent: () -> AnalyticsConsentState = ::currentConsent,
    ) {
        CrashReportingConsent(collection, isDebugBuild).apply(consent())
    }

    companion object {
        val shared = ProcessDiagnostics()

        /**
         * PD-1: default-DENY, globally. The ONE consent signal shared by analytics and Crashlytics; a future
         * consent surface plugs in here.
         */
        fun currentConsent(): AnalyticsConsentState = AnalyticsConsentState.UNKNOWN
    }
}
