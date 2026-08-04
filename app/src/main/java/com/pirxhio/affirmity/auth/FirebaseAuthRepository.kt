package com.pirxhio.affirmity.auth

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Plain snapshot of the fields [FirebaseAuthRepository] needs from a Firebase user. */
internal data class UserSnapshot(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val photoUrl: String? = null,
)

/**
 * Narrow seam over [FirebaseAuth] so state-mapping and provider-lookup logic in
 * [FirebaseAuthRepository] is unit-testable on the JVM without a real Firebase/Android
 * dependency (see design.md Testing Strategy).
 */
internal interface FirebaseAuthSource {
    fun currentUserSnapshot(): UserSnapshot?
    fun addAuthStateListener(onChange: (UserSnapshot?) -> Unit)
    suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit>
    fun signOut()
}

private fun FirebaseUser.toSnapshot(): UserSnapshot =
    UserSnapshot(uid = uid, displayName = displayName, email = email, photoUrl = photoUrl?.toString())

private class RealFirebaseAuthSource(private val auth: FirebaseAuth) : FirebaseAuthSource {
    override fun currentUserSnapshot(): UserSnapshot? = auth.currentUser?.toSnapshot()

    override fun addAuthStateListener(onChange: (UserSnapshot?) -> Unit) {
        auth.addAuthStateListener { firebaseAuth -> onChange(firebaseAuth.currentUser?.toSnapshot()) }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> = runCatching {
        auth.signInWithCredential(googleCredentialFor(idToken)).await()
        Unit
    }

    override fun signOut() = auth.signOut()
}

/** Small, isolated Firebase SDK call — the only place [GoogleAuthProvider] is referenced. */
private fun googleCredentialFor(idToken: String) = GoogleAuthProvider.getCredential(idToken, null)

/** Maps a raw [UserSnapshot] into the provider-neutral [AuthState] — pure, no Firebase/Android deps. */
internal fun mapSnapshot(snapshot: UserSnapshot?): AuthState =
    if (snapshot == null) {
        AuthState.SignedOut
    } else {
        AuthState.SignedIn(
            uid = snapshot.uid,
            displayName = snapshot.displayName,
            email = snapshot.email,
            photoUrl = snapshot.photoUrl,
        )
    }

/**
 * [AuthRepository] backed by [FirebaseAuth]. Mirrors [FirebaseAuth.addAuthStateListener] into a
 * [StateFlow] and maps a [ProviderCredential.IdToken] onto `signInWithCredential`.
 */
class FirebaseAuthRepository internal constructor(
    private val providers: Map<AuthProviderId, AuthProvider>,
    private val source: FirebaseAuthSource,
) : AuthRepository {

    constructor(auth: FirebaseAuth, providers: Map<AuthProviderId, AuthProvider>) :
        this(providers, RealFirebaseAuthSource(auth))

    private val _authState = MutableStateFlow(mapSnapshot(source.currentUserSnapshot()))
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        source.addAuthStateListener { snapshot -> _authState.value = mapSnapshot(snapshot) }
    }

    override suspend fun signIn(provider: AuthProviderId, activityContext: Context): Result<Unit> {
        val authProvider = providers[provider]
            ?: return Result.failure(IllegalArgumentException("No AuthProvider registered for $provider"))
        return authProvider.requestCredential(activityContext).fold(
            onSuccess = { credential -> signInWithCredential(credential) },
            onFailure = { Result.failure(it) },
        )
    }

    private suspend fun signInWithCredential(credential: ProviderCredential): Result<Unit> =
        when (credential) {
            is ProviderCredential.IdToken -> source.signInWithGoogleIdToken(credential.token)
        }

    override suspend fun signOut() {
        source.signOut()
    }
}
