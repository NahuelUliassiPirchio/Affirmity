package com.pirxhio.affirmity.auth

import android.content.Context

/** One identity-provider credential collector (e.g. Google via Credential Manager). */
interface AuthProvider {
    val id: AuthProviderId

    /** Collects a [ProviderCredential] from the platform UI; the caller maps failures to [AuthError]. */
    suspend fun requestCredential(activityContext: Context): Result<ProviderCredential>
}
