package com.pirxhio.affirmity.auth

/** Identity providers supported by [AuthRepository.signIn]. APPLE is a future stage. */
enum class AuthProviderId { GOOGLE }

/** Provider-neutral observable auth state — no provider-specific identifiers or terminology. */
sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(
        val uid: String,
        val displayName: String?,
        val email: String?,
        val photoUrl: String? = null,
    ) : AuthState
}

/** Provider-neutral credential shapes; Apple later adds an OAuth web-flow branch. */
sealed interface ProviderCredential {
    data class IdToken(val providerId: String, val token: String) : ProviderCredential
}

/** Recoverable sign-in failure reasons surfaced to the UI; never a crash. */
sealed interface AuthError {
    data object NoCredentialAvailable : AuthError
    data object ProviderUnavailable : AuthError
    data object ConfigurationMissing : AuthError
    data class Unknown(val message: String?) : AuthError
}
