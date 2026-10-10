package com.pirxhio.affirmity.data.catalog

import android.util.Log
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Owns the in-app language switch (design D5): pre-seed the TARGET catalog, apply the app locale,
 * then reconcile against whatever locale actually became effective.
 *
 * Runs in a PROCESS scope so neither leaving the Settings composition nor the activity recreate
 * (triggered by [applyLocales]) can cancel the seed or skip the apply. The pre-seed is only
 * AWAITED up to [timeoutMs], never cancelled: a slow device keeps its work (no rollback, no redo)
 * and the recreated UI converges through the table Flow when the replace commits.
 */
class CatalogLocaleSwitcher(
    private val scope: CoroutineScope,
    private val seeder: CatalogSeeder,
    private val applyLocales: (LocaleListCompat) -> Unit,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val seedDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** [target] MUST be the explicit locale the user picked (never the no-arg resolver, which still
     *  answers with the OLD locale before [applyLocales] runs). */
    fun switch(target: CatalogLocale, locales: LocaleListCompat): Job = scope.launch {
        val preSeed = async(seedDispatcher) { runLogged("pre-seed ${target.tag}") { seeder.seedIfNeeded(target) } }
        withTimeoutOrNull(timeoutMs) { preSeed.await() } // await only: a timeout does not cancel the seed
        applyLocales(locales)
        // Reconcile: resolves the really-effective locale inside the seeder lock. No-op when the
        // switch took; re-seeds back if applyLocales was a no-op / produced no recreate.
        // Off the (Main) process dispatcher: the seeder reads + parses the asset before checking the marker.
        runLogged("reconcile") { withContext(seedDispatcher) { seeder.seedIfNeeded() } }
    }

    private suspend fun runLogged(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Log.e(TAG, "catalog locale switch: $what failed", error)
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 1_500L
        private const val TAG = "CatalogLocaleSwitcher"

        /** Outlives any composition or activity; shared by every [CatalogLocaleSwitcher] in the app. */
        val processScope: CoroutineScope by lazy {
            CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        }
    }
}
