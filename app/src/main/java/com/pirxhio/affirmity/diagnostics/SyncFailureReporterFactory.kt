package com.pirxhio.affirmity.diagnostics

import com.google.firebase.crashlytics.FirebaseCrashlytics

/** The production composition: Crashlytics, limited to one report per distinct failure per run. */
fun createSyncFailureReporter(
    crashlytics: () -> FirebaseCrashlytics = { FirebaseCrashlytics.getInstance() },
): SyncFailureReporter = OncePerRunSyncFailureReporter(CrashlyticsSyncFailureReporter(crashlytics))
