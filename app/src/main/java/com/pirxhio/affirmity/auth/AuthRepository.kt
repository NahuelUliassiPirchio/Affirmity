package com.pirxhio.affirmity.auth

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

/**
 * Provider-agnostic sign-in/sign-out/observable-state contract. The public API never names a
 * specific identity provider — only [AuthProviderId] selects which [AuthProvider] runs.
 */
interface AuthRepository {
    val authState: StateFlow<AuthState>

    /** Delegates credential collection to the [AuthProvider] registered for [provider]. */
    suspend fun signIn(provider: AuthProviderId, activityContext: Context): Result<Unit>

    suspend fun signOut()
}
