package com.pirxhio.affirmity.ads

import android.content.Context
import com.google.android.gms.ads.MobileAds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/**
 * Process-scoped, idempotent `MobileAds.initialize` guard (design D6). "Once per process, on
 * demand" is a process fact, not a composition fact, so this deliberately lives outside any
 * `@Composable` — a `remember { }` flag would re-init on every screen entry instead.
 *
 * [GoogleRewardedAdGateway] keeps its own separate init guard untouched; `MobileAds.initialize`
 * is natively idempotent so the two guards coexist safely. Follow-up: fold both onto a single
 * shared `AdConsentGateway` object once extracted.
 */
internal object MobileAdsInitializer {

    private val mutex = Mutex()
    @Volatile private var initialized = false

    suspend fun ensureInitialized(context: Context) {
        if (initialized) return
        mutex.withLock {
            if (initialized) return@withLock
            suspendCancellableCoroutine<Unit> { continuation ->
                MobileAds.initialize(context) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
            initialized = true
        }
    }
}
