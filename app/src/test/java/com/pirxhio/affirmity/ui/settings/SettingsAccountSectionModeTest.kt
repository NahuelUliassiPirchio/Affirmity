package com.pirxhio.affirmity.ui.settings

import com.pirxhio.affirmity.auth.AuthState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bug 1: a guest who finishes onboarding via "continue without account" has no way back into
 * Settings' account section to sign in -- it only ever rendered a sign-out row, gated on
 * `authState is AuthState.SignedIn`, with no `else` branch. [settingsAccountSectionMode] is the
 * pure branch-selection logic extracted out of the composable so this is JVM-testable (pattern:
 * `resolveSelectedGroupIds` in `data/AffirmityAppState.kt`).
 */
class SettingsAccountSectionModeTest {

    @Test
    fun `signed out renders the sign-in mode`() {
        assertEquals(
            SettingsAccountSectionMode.SIGNED_OUT,
            settingsAccountSectionMode(AuthState.SignedOut),
        )
    }

    @Test
    fun `signed in renders the sign-out mode`() {
        val signedIn = AuthState.SignedIn(uid = "uid-1", displayName = "Ana", email = "ana@example.com")
        assertEquals(
            SettingsAccountSectionMode.SIGNED_IN,
            settingsAccountSectionMode(signedIn),
        )
    }
}
