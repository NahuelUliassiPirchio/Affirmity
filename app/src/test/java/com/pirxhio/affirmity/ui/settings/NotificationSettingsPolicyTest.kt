package com.pirxhio.affirmity.ui.settings

import com.pirxhio.affirmity.auth.AuthState
import com.pirxhio.affirmity.data.canPersistQuietHoursSettings
import com.pirxhio.affirmity.data.local.DaySegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSettingsPolicyTest {

    @Test
    fun `notification controls require a signed-in user`() {
        assertFalse(notificationControlsEnabled(AuthState.SignedOut))
        assertTrue(
            notificationControlsEnabled(
                AuthState.SignedIn(uid = "uid", displayName = null, email = null),
            ),
        )
    }

    @Test
    fun `quiet-hours window changes are rejected after controls become disabled`() {
        assertFalse(canApplyQuietHoursWindowChange(controlsEnabled = false))
        assertTrue(canApplyQuietHoursWindowChange(controlsEnabled = true))
    }

    @Test
    fun `state-layer quiet-hours persistence requires an authenticated user`() {
        assertFalse(canPersistQuietHoursSettings(AuthState.SignedOut))
        assertTrue(
            canPersistQuietHoursSettings(
                AuthState.SignedIn(uid = "uid", displayName = null, email = null),
            ),
        )
    }

    @Test
    fun `deselecting the last active segment is a no-op`() {
        val current = setOf(DaySegment.NOCHE)

        assertEquals(current, toggledNotificationSegments(current, DaySegment.NOCHE))
    }

    @Test
    fun `deselecting one of several segments removes it`() {
        val current = setOf(DaySegment.MANANA, DaySegment.NOCHE)

        assertEquals(setOf(DaySegment.NOCHE), toggledNotificationSegments(current, DaySegment.MANANA))
    }

    @Test
    fun `selecting an inactive segment adds it`() {
        assertEquals(
            setOf(DaySegment.MANANA, DaySegment.NOCHE),
            toggledNotificationSegments(setOf(DaySegment.NOCHE), DaySegment.MANANA),
        )
    }
}
