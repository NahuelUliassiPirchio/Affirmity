package com.pirxhio.affirmity.auth

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

/** Fake behind the narrow [FirebaseAuthSource] seam — no real Firebase/Android dependency. */
private class FakeFirebaseAuthSource(
    initialSnapshot: UserSnapshot? = null,
) : FirebaseAuthSource {
    private var listener: ((UserSnapshot?) -> Unit)? = null
    private var current: UserSnapshot? = initialSnapshot
    var signInCalls = 0
        private set
    var lastIdToken: String? = null
    var signOutCalls = 0
        private set
    var signInResult: Result<Unit> = Result.success(Unit)

    override fun currentUserSnapshot(): UserSnapshot? = current

    override fun addAuthStateListener(onChange: (UserSnapshot?) -> Unit) {
        listener = onChange
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<Unit> {
        signInCalls++
        lastIdToken = idToken
        return signInResult
    }

    override fun signOut() {
        signOutCalls++
        current = null
        listener?.invoke(null)
    }

    fun emit(snapshot: UserSnapshot?) {
        current = snapshot
        listener?.invoke(snapshot)
    }
}

private class FakeAuthProvider(
    override val id: AuthProviderId,
    private val result: Result<ProviderCredential>,
) : AuthProvider {
    override suspend fun requestCredential(activityContext: Context): Result<ProviderCredential> = result
}

private fun fakeContext(): Context = mock(Context::class.java)

class FirebaseAuthRepositoryTest {

    @Test
    fun `initial state reflects a non-null current user as SignedIn`() {
        val source = FakeFirebaseAuthSource(
            initialSnapshot = UserSnapshot(uid = "uid-1", displayName = "Ana", email = "ana@example.com"),
        )
        val repository = FirebaseAuthRepository(providers = emptyMap(), source = source)

        val state = repository.authState.value
        assertTrue(state is AuthState.SignedIn)
        state as AuthState.SignedIn
        assertEquals("uid-1", state.uid)
        assertEquals("Ana", state.displayName)
        assertEquals("ana@example.com", state.email)
    }

    @Test
    fun `initial state reflects a null current user as SignedOut`() {
        val source = FakeFirebaseAuthSource(initialSnapshot = null)
        val repository = FirebaseAuthRepository(providers = emptyMap(), source = source)

        assertEquals(AuthState.SignedOut, repository.authState.value)
    }

    @Test
    fun `auth state listener transitions from SignedOut to SignedIn when a user appears`() {
        val source = FakeFirebaseAuthSource(initialSnapshot = null)
        val repository = FirebaseAuthRepository(providers = emptyMap(), source = source)

        source.emit(UserSnapshot(uid = "uid-2", displayName = null, email = null))

        val state = repository.authState.value
        assertTrue(state is AuthState.SignedIn)
        assertEquals("uid-2", (state as AuthState.SignedIn).uid)
    }

    @Test
    fun `signIn on an unregistered provider fails without touching the auth source`() = runBlocking {
        val source = FakeFirebaseAuthSource()
        val repository = FirebaseAuthRepository(providers = emptyMap(), source = source)

        val result = repository.signIn(AuthProviderId.GOOGLE, activityContext = fakeContext())

        assertTrue(result.isFailure)
        assertEquals(0, source.signInCalls)
    }

    @Test
    fun `signIn maps a successful IdToken credential into a Firebase sign-in call`() = runBlocking {
        val source = FakeFirebaseAuthSource()
        val provider = FakeAuthProvider(
            id = AuthProviderId.GOOGLE,
            result = Result.success(ProviderCredential.IdToken(providerId = "google.com", token = "token-abc")),
        )
        val repository = FirebaseAuthRepository(providers = mapOf(AuthProviderId.GOOGLE to provider), source = source)

        val result = repository.signIn(AuthProviderId.GOOGLE, activityContext = fakeContext())

        assertTrue(result.isSuccess)
        assertEquals(1, source.signInCalls)
        assertEquals("token-abc", source.lastIdToken)
    }

    @Test
    fun `signOut delegates to the auth source and returns to SignedOut`() = runBlocking {
        val source = FakeFirebaseAuthSource(
            initialSnapshot = UserSnapshot(uid = "uid-3", displayName = null, email = null),
        )
        val repository = FirebaseAuthRepository(providers = emptyMap(), source = source)

        repository.signOut()

        assertEquals(1, source.signOutCalls)
        assertEquals(AuthState.SignedOut, repository.authState.value)
    }
}
