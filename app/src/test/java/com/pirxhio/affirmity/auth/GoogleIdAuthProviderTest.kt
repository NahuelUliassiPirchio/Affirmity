package com.pirxhio.affirmity.auth

import android.content.Context
import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class GoogleIdAuthProviderTest {

    @Test
    fun `classifyCredentialError maps user cancellation to no error`() {
        val error = classifyCredentialError(type = "androidx.credentials.TYPE_USER_CANCELED", message = "cancelled")

        assertNull(error)
    }

    @Test
    fun `classifyCredentialError maps no-credential type to NoCredentialAvailable`() {
        val error = classifyCredentialError(type = "android.credentials.GetCredentialException.TYPE_NO_CREDENTIAL", message = null)

        assertEquals(AuthError.NoCredentialAvailable, error)
    }

    @Test
    fun `classifyCredentialError maps a provider-configuration type to ProviderUnavailable`() {
        val error = classifyCredentialError(
            type = "androidx.credentials.TYPE_PROVIDER_CONFIGURATION_ERROR",
            message = null,
        )

        assertEquals(AuthError.ProviderUnavailable, error)
    }

    @Test
    fun `classifyCredentialError maps a Play Services message to ProviderUnavailable`() {
        val error = classifyCredentialError(type = "androidx.credentials.TYPE_UNKNOWN", message = "Google Play Services is out of date")

        assertEquals(AuthError.ProviderUnavailable, error)
    }

    @Test
    fun `classifyCredentialError falls back to Unknown with the original message`() {
        val error = classifyCredentialError(type = "androidx.credentials.TYPE_UNKNOWN", message = "something else broke")

        assertEquals(AuthError.Unknown("something else broke"), error)
    }

    @Test
    fun `webClientId returns null when the resource does not exist`() {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(context.resources).thenReturn(resources)
        `when`(context.packageName).thenReturn("com.pirxhio.affirmity")
        `when`(resources.getIdentifier("default_web_client_id", "string", "com.pirxhio.affirmity")).thenReturn(0)

        assertNull(webClientId(context))
    }

    @Test
    fun `webClientId returns null when the resource resolves to a blank string`() {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(context.resources).thenReturn(resources)
        `when`(context.packageName).thenReturn("com.pirxhio.affirmity")
        `when`(resources.getIdentifier("default_web_client_id", "string", "com.pirxhio.affirmity")).thenReturn(42)
        `when`(context.getString(42)).thenReturn("   ")

        assertNull(webClientId(context))
    }

    @Test
    fun `webClientId returns the resolved string when the resource exists and is non-blank`() {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        `when`(context.resources).thenReturn(resources)
        `when`(context.packageName).thenReturn("com.pirxhio.affirmity")
        `when`(resources.getIdentifier("default_web_client_id", "string", "com.pirxhio.affirmity")).thenReturn(42)
        `when`(context.getString(42)).thenReturn("web-client-id-123")

        assertEquals("web-client-id-123", webClientId(context))
    }
}
