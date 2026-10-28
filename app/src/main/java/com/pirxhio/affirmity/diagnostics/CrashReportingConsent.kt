package com.pirxhio.affirmity.diagnostics

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.pirxhio.affirmity.analytics.AnalyticsConsentState

/** SDK boundary for Crashlytics collection, so consent mapping is testable on the JVM. */
interface CrashlyticsCollection {
    fun setEnabled(enabled: Boolean)
    fun deleteUnsentReports()
}

class FirebaseCrashlyticsCollection(
    private val crashlytics: () -> FirebaseCrashlytics = { FirebaseCrashlytics.getInstance() },
) : CrashlyticsCollection {
    override fun setEnabled(enabled: Boolean) = guarded { crashlytics().setCrashlyticsCollectionEnabled(enabled) }
    override fun deleteUnsentReports() = guarded { crashlytics().deleteUnsentReports() }

    private inline fun guarded(block: () -> Unit) {
        try { block() } catch (_: RuntimeException) { /* uninitialized FirebaseApp: nothing to configure */ }
    }
}

/**
 * Maps the analytics consent signal onto Crashlytics collection. The manifest default is disabled, so only
 * GRANTED in a non-debug build ever enables it; anything else disables it and drops queued reports.
 * Call [apply] whenever the consent state changes.
 */
class CrashReportingConsent(
    private val collection: CrashlyticsCollection,
    private val isDebugBuild: Boolean,
) {
    fun apply(state: AnalyticsConsentState) {
        if (state == AnalyticsConsentState.GRANTED && !isDebugBuild) {
            collection.setEnabled(true)
        } else {
            collection.setEnabled(false)
            collection.deleteUnsentReports()
        }
    }
}
